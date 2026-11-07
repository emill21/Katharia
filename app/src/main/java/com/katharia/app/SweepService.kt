package com.katharia.app

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Does the actual sweeping. Android doesn't let ordinary apps force-stop other apps or
 * clear their cache, so this service opens each app's page in Settings and taps the
 * same buttons you would. It sits idle until the user starts a sweep.
 */
class SweepService : AccessibilityService() {

    private val scope = MainScope()
    private var job: Job? = null
    private lateinit var text: SettingsText
    private var overlay: View? = null
    private var overlayText: TextView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        text = SettingsText(this)
        instance = this
    }

    /** Pinged on every screen change while sweeping, so waits end the instant Settings updates. */
    private val changed = Channel<Unit>(Channel.CONFLATED)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (job?.isActive == true) changed.trySend(Unit)
    }
    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        hideOverlay()
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- sweep loop

    fun start(targets: List<Target>, mode: SweepMode, fromShade: Boolean = false) {
        if (job?.isActive == true || targets.isEmpty()) return
        text = SettingsText(this) // pick up a language change
        val list = targets.filter { it.packageName != packageName && it.packageName != SettingsText.SETTINGS }
        job = scope.launch {
            val outcomes = mutableListOf<AppOutcome>()
            var cancelled = false
            val started = System.currentTimeMillis()
            try {
                if (fromShade) {
                    // Started from the Quick Settings tile: get the shade out of the way first.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                    } else {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                    delay(350)
                }
                activeSet = if (mode == SweepMode.Running) withContext(Dispatchers.IO) {
                    AppRepo.recentlyActive(this@SweepService, Prefs(this@SweepService).lastStopSweepAt)
                } else null
                // Start straight away: cache is measured per app as we go, not all up front.
                showOverlay()
                list.forEachIndexed { i, t ->
                    Sweep.set(SweepState.Running(mode, i, list.size, t.label, outcomes.takeLast(4).reversed()))
                    overlayText?.text = "Sweeping ${i + 1} of ${list.size}\n${t.label}"
                    outcomes += sweepOne(t, mode)
                }
            } catch (e: CancellationException) {
                cancelled = true
            } finally {
                withContext(NonCancellable) {
                    hideOverlay()
                    // Freed = cache before minus cache now, for the apps we actually cleared.
                    val cleared = outcomes.filter { it.cache == StepResult.Done && it.cacheBefore != null }
                    val freed = if (!mode.clearCache || !AppRepo.hasUsageAccess(this@SweepService)) null
                    else withContext(Dispatchers.IO) {
                        cleared.sumOf { o ->
                            (o.cacheBefore!! - (AppRepo.cacheOf(this@SweepService, o.target.packageName) ?: o.cacheBefore)).coerceAtLeast(0)
                        }
                    }
                    val done = SweepState.Finished(mode, outcomes.toList(), cancelled, freed, System.currentTimeMillis() - started)
                    Prefs(this@SweepService).record(done)
                    Sweep.set(done)
                    backToApp()
                }
            }
        }
    }

    /** Sweeps the selection saved in the app (tile and shortcut use this). */
    fun startSaved(fromShade: Boolean) {
        if (job?.isActive == true) return
        scope.launch {
            val prefs = Prefs(this@SweepService)
            val apps = withContext(Dispatchers.IO) { AppRepo.load(this@SweepService, prefs.includeSystem) }
            val targets = apps.filter { it.packageName !in prefs.excluded }
                .map { Target(it.packageName, it.label, it.protected) }
            start(targets, prefs.mode, fromShade)
        }
    }

    val isRunning get() = job?.isActive == true

    fun stop() {
        job?.cancel()
    }

    /** Apps opened or running something in the foreground recently (for "Close running"). */
    private var activeSet: Set<String>? = null

    private suspend fun sweepOne(t: Target, mode: SweepMode): AppOutcome {
        val t0 = System.currentTimeMillis()
        val trace = StringBuilder()
        fun mark(what: String) { trace.append(what).append(' ').append(System.currentTimeMillis() - t0).append("ms · ") }

        // Skip Settings entirely when we already know there's nothing to do — the biggest speed win.
        val wantStop = mode.forceStop && !t.protect
        val (needStop, cacheNow) = withContext(Dispatchers.IO) {
            val stopped = AppRepo.isStopped(this@SweepService, t.packageName)
            val active = activeSet?.let { t.packageName in it } ?: true
            (wantStop && !stopped && (mode != SweepMode.Running || active)) to
                (if (mode.clearCache) AppRepo.cacheOf(this@SweepService, t.packageName) else null)
        }
        val needCache = mode.clearCache && (cacheNow == null || cacheNow >= MIN_CACHE_BYTES)
        fun done(stop: StepResult, cache: StepResult) =
            AppOutcome(t, stop, cache, cacheNow, System.currentTimeMillis() - t0, trace.toString().trimEnd(' ', '·'))
        if (!needStop && !needCache) {
            mark("skipped")
            return done(
                if (wantStop) StepResult.AlreadyClean else StepResult.Skipped,
                if (mode.clearCache) StepResult.AlreadyClean else StepResult.Skipped,
            )
        }

        // A new App info page is a new window (or shows the app's name), so we know the instant it's up.
        val oldWindows = snap().windowIds
        while (changed.tryReceive().isSuccess) Unit
        openAppInfo(t.packageName, clearTask = false)
        var ready = poll(1800) { pageReady(oldWindows, t.label) }
        if (ready == null) {
            mark("relaunch")
            val old2 = snap().windowIds
            openAppInfo(t.packageName, clearTask = true)
            ready = poll(3500) { pageReady(old2, t.label) }
        }
        if (ready == null) {
            mark("page never loaded")
            return done(notFoundIf(needStop), notFoundIf(needCache))
        }
        mark("page")

        val stop = when {
            needStop -> tapForceStop().also { mark("stop:$it") }
            wantStop -> StepResult.AlreadyClean
            else -> StepResult.Skipped
        }
        val cache = when {
            needCache -> tapClearCache(knownCache = cacheNow) { mark(it) }.also { mark("cache:$it") }
            mode.clearCache -> StepResult.AlreadyClean
            else -> StepResult.Skipped
        }
        return done(stop, cache)
    }

    private fun pageReady(oldWindows: Set<Int>, label: String): Hit? {
        val sc = snap()
        val fresh = sc.windowIds.any { it !in oldWindows }
        if (!fresh && !sc.hasText(label.normalized())) return null
        return sc.find(text.forceStop) ?: sc.find(text.storage) ?: sc.find(text.clearCache)
    }

    private fun notFoundIf(b: Boolean) = if (b) StepResult.NotFound else StepResult.Skipped

    private suspend fun tapForceStop(): StepResult {
        val btn = poll(2000) { snap().find(text.forceStop) } ?: return StepResult.NotFound
        if (!btn.enabled) return StepResult.AlreadyClean // already stopped, or a protected app
        btn.click()

        val ok = poll(2000) { snap().findConfirm(btn) }
        if (ok != null) {
            ok.click()
            // Move on the moment the dialog is gone.
            poll(700) { snap().takeIf { it.findConfirm(btn) == null } }
            return StepResult.Done
        }
        // Some skins stop the app straight away with no dialog; the button greys out.
        val greyed = poll(1000) { snap().find(text.forceStop)?.takeIf { !it.enabled } }
        return if (greyed != null) StepResult.Done else StepResult.NotFound
    }

    private suspend fun tapClearCache(knownCache: Long?, note: (String) -> Unit): StepResult {
        var clear = snap().find(text.clearCache) // a few skins show it right on App info
        if (clear == null) {
            val storage = findWithScroll(text.storage, ordered = true)
                ?: snap().findStartingWith(text.storagePrefix).also { if (it != null) note("storage~") }
            if (storage != null) {
                storage.click()
                // Wait for the storage page itself (its Cache row), not just the button — on Android 16+
                // the buttons sit below the size breakdown, off screen.
                var page = poll(2200) { snap().takeIf { it.onStoragePage() } }
                if (page == null && snap().find(text.forceStop, includeHidden = true) != null) {
                    // Still on App info: the tap landed while the page was settling. Tap the row again.
                    (snap().findOrdered(text.storage) ?: snap().findStartingWith(text.storagePrefix))?.let { it.click(); note("retap") }
                    page = poll(2500) { snap().takeIf { it.onStoragePage() } }
                }
                note("storage page")
                clear = revealClearCache(note)
                if (clear == null) {
                    // Android 17: no buttons on this page any more — the Cache row itself clears the cache.
                    return clearViaCacheRow(note)
                }
            } else if (snap().packages.any { it == MIUI }) {
                // Xiaomi / HyperOS: Clear data → menu → Clear cache. (Here "Clear data" only opens a menu.)
                val menu = snap().find(text.miuiClearData, allowForbidden = true)
                if (menu == null) { note("miui: no clear-data menu"); return StepResult.NotFound }
                menu.click()
                clear = poll(2500) { snap().find(text.clearCache) }
                if (clear == null) { note("miui: no clear-cache item"); return StepResult.NotFound }
                note("miui")
            } else {
                note("no storage row; screen: ${snap().describe()}")
                return StepResult.NotFound
            }
        }
        // Settings greys the button out until it has measured the cache.
        if (!clear.enabled) {
            clear = poll(if (knownCache != null) 2500 else 1200) { findClearCache(includeHidden = true)?.takeIf { it.enabled } }
            if (clear == null) { note("clear-cache greyed out"); return StepResult.AlreadyClean }
        }
        val readingBefore = snap().cacheReading()
        val before = snap().windowIds
        if (!clear.click()) note("click refused")

        // Either the button greys out (cleared — the usual case), or a "Clear cache?" dialog pops up
        // (Xiaomi, ColorOS…), which we confirm. Whichever happens first.
        var confirmed = false
        val cleared = poll(1500) {
            val sc = snap()
            val dialog = if (!confirmed) sc.findDialogButton(before, text.cacheConfirm, exclude = clear) else null
            if (dialog != null) {
                dialog.click(); confirmed = true; note("confirmed"); null
            } else {
                val btn = sc.find(text.clearCache, includeHidden = true) ?: sc.findCacheLike(includeHidden = true)
                val reading = sc.cacheReading()
                when {
                    btn == null || !btn.enabled -> true
                    readingBefore != null && reading != null && reading != readingBefore -> true
                    else -> null
                }
            }
        }
        val readingAfter = snap().cacheReading()
        if (readingBefore != null) note("cache $readingBefore → ${readingAfter ?: "?"}")
        if (cleared == null) note("unverified")
        return StepResult.Done
    }

    /**
     * Android 17 layout: the storage page is just a size breakdown (App size / User data / Cache / Total)
     * and clearing happens by tapping a row. We only ever tap the *Cache* row — never User data, which
     * would wipe the app — and only confirm a dialog that is clearly about cache.
     */
    private suspend fun clearViaCacheRow(note: (String) -> Unit): StepResult {
        val sc = snap()
        val row = sc.cacheRowTarget()
        if (row == null) {
            val withheld = sc.items.sumOf { it.withheld }
            if (withheld > 0) note("Android withheld $withheld controls")
            note("no clear-cache button or tappable cache row; screen: ${sc.describe()}")
            return StepResult.NotFound
        }
        val readingBefore = sc.cacheReading()
        if (readingBefore != null && readingBefore.startsWith("0")) { note("cache row says $readingBefore"); return StepResult.AlreadyClean }
        val before = sc.windowIds
        row.click()
        note("cache row")

        // Expect a "Clear cache?" dialog, a sheet with a Clear cache button, or the size simply dropping.
        var acted = false
        val result = poll(2500) {
            val now = snap()
            val reading = now.cacheReading()
            if (readingBefore != null && reading != null && reading != readingBefore) return@poll "cleared"
            if (!acted) {
                val dialog = now.findSafeCacheConfirm(before)
                if (dialog != null) { dialog.click(); acted = true; note("confirmed"); return@poll null }
                if (now.dialogLooksDangerous(before)) return@poll "danger"
                val button = now.find(text.clearCache) ?: now.findCacheLike()
                if (button != null && button.enabled) { button.click(); acted = true; note("sheet button"); return@poll null }
            }
            null
        }
        if (result == "danger") {
            // Something that isn't about cache popped up. Back out without touching it.
            performGlobalAction(GLOBAL_ACTION_BACK)
            note("unexpected dialog, backed out; screen: ${snap().describe()}")
            return StepResult.NotFound
        }
        val readingAfter = snap().cacheReading()
        if (readingBefore != null) note("cache $readingBefore → ${readingAfter ?: "?"}")
        return when {
            result == "cleared" || acted -> StepResult.Done
            else -> { note("cache row did nothing; screen: ${snap().describe()}"); StepResult.NotFound }
        }
    }

    /**
     * Gets the Clear cache button onto the screen. It may already be visible; it may be in the page
     * but below the fold (we ask Android to scroll it into view); or the list may not have built it yet
     * (we scroll the page down until it appears).
     */
    private suspend fun revealClearCache(note: (String) -> Unit): Hit? {
        poll(600) { findClearCache() }?.let { return it }
        repeat(8) { attempt ->
            val sc = snap()
            val hidden = sc.find(text.clearCache, includeHidden = true) ?: sc.findCacheLike(includeHidden = true)
            if (hidden != null) {
                if (hidden.visible) return hidden
                hidden.node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                poll(500) { findClearCache() }?.let { note("revealed"); return it }
                // Couldn't scroll it into view; the tap still works on most screens.
                note("tap off-screen")
                return hidden
            }
            val scroller = sc.scrollers().firstOrNull()
            if (scroller == null) {
                if (attempt == 0) poll(800) { findClearCache() }?.let { return it }
                return null
            }
            val sig = sc.signature()
            val moved = scroller.node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ||
                scroller.node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id)
            if (!moved) return poll(600) { findClearCache(includeHidden = true) }
            note("scroll")
            poll(450) { findClearCache() }?.let { return it }
            if (snap().signature() == sig) return null // reached the bottom; nothing more to find
        }
        return findClearCache(includeHidden = true)
    }

    /** "Clear cache", or anything close to it ("Clear app cache", "Clear cached data"…). */
    private fun findClearCache(includeHidden: Boolean = false): Hit? =
        snap().let { it.find(text.clearCache, includeHidden = includeHidden) ?: it.findCacheLike(includeHidden) }

    private suspend fun findWithScroll(labels: Set<String>, ordered: Boolean = false): Hit? {
        repeat(6) {
            val sc = snap()
            (if (ordered) sc.findOrdered(labels) else sc.find(labels))?.let { return it }
            val scroller = sc.scrollers().firstOrNull() ?: return poll(1200) { snap().find(labels) }
            scroller.node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            poll(300) { snap().find(labels) }?.let { return it }
        }
        return null
    }

    private fun openAppInfo(pkg: String, clearTask: Boolean) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
            .addFlags(
                // CLEAR_TOP swaps the App info page in place inside Settings' own task; CLEAR_TASK is the fallback.
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    (if (clearTask) Intent.FLAG_ACTIVITY_CLEAR_TASK else Intent.FLAG_ACTIVITY_CLEAR_TOP) or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        runCatching { startActivity(i) }
    }

    private fun backToApp() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
        }
    }

    // ---------------------------------------------------------------- reading the screen
    //
    // Newer Settings (Android 14+ on Pixel and others) is built with Jetpack Compose, where Android's
    // built-in "find by text" search returns nothing. So instead we take one snapshot of the screen per
    // check — every visible element with its label, parent and state — and search that locally. It works
    // on both old and new Settings, and one walk is faster than many separate searches.

    private class Item(
        val node: AccessibilityNodeInfo,
        val label: String,
        val parent: Int,
        val id: String?,
        val window: Int,
        /** On screen right now. Off-screen items (e.g. buttons below the fold) are kept so we can scroll to them. */
        val visible: Boolean,
    ) {
        /** Children Android reported but wouldn't hand over (e.g. controls marked sensitive). */
        var withheld: Int = 0
    }

    inner class Hit(val node: AccessibilityNodeInfo, val enabled: Boolean, val visible: Boolean = true) {
        fun click() = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private inner class Screen(val items: List<Item>, val windowIds: Set<Int>, val packages: Set<String>) {
        /** What's on screen, cheaply, to tell whether a scroll actually moved anything. */
        fun signature() = items.filter { it.visible }.joinToString("|") { it.label }

        fun hasText(s: String) = items.any { it.visible && it.label.contains(s) }

        /** First visible element whose label is one of [labels], resolved to the thing you'd tap. */
        fun find(labels: Set<String>, allowForbidden: Boolean = false, includeHidden: Boolean = false): Hit? {
            for (i in items.indices) {
                if (!includeHidden && !items[i].visible) continue
                if (matches(items[i].label, labels)) tappable(i, allowForbidden)?.let { return it }
            }
            return null
        }

        /** Size shown next to the "Cache" row on the storage page ("173 mb"), for the log. */
        fun cacheReading(): String? {
            val i = items.indexOfFirst { it.label in text.cacheRow }
            return if (i >= 0) items.getOrNull(i + 1)?.label?.takeIf { it.any(Char::isDigit) } else null
        }

        /** Signs we're on an app's storage page even before its buttons are in view. */
        fun onStoragePage() = items.any { it.label in text.cacheRow } || find(text.clearCache, includeHidden = true) != null

        fun scrollers() = items.filter { it.node.isScrollable }.sortedByDescending {
            val r = android.graphics.Rect(); it.node.getBoundsInScreen(r); r.height().toLong() * r.width()
        }

        /** Like [find], but tries labels in priority order ("Storage & cache" before a bare "Storage"). */
        fun findOrdered(labels: Set<String>): Hit? {
            for (l in labels) {
                val one = setOf(l)
                for (i in items.indices) if (items[i].visible && matches(items[i].label, one)) tappable(i)?.let { return it }
            }
            return null
        }

        /** A row whose label starts with one of [prefixes], e.g. "Storage usage" — never a "Clear…" button. */
        fun findStartingWith(prefixes: Set<String>): Hit? {
            for (i in items.indices) {
                val l = items[i].label
                if (!items[i].visible) continue
                if (prefixes.any { l.startsWith(it) } && !matches(l, text.forbidden)) tappable(i)?.let { return it }
            }
            return null
        }

        /** A button mentioning cache next to a "clear"-type word — never one that clears storage or data. */
        fun findCacheLike(includeHidden: Boolean = false): Hit? {
            for (i in items.indices) {
                if (!includeHidden && !items[i].visible) continue
                val l = items[i].label
                if (l.length > 40 || !l.contains("cache")) continue
                if (!text.clearWords.any { l.contains(it) }) continue
                if (matches(l, text.forbidden) || l.contains("storage") || l.contains("data")) continue
                tappable(i)?.let { return it }
            }
            return null
        }

        /**
         * What's on screen, for the sweep log: labels, "~" = off screen, "*" = tappable,
         * "∅*Class" = a tappable element with no label (e.g. an icon button).
         */
        fun describe(): String = items.asSequence()
            .filter { it.label.isNotBlank() || it.node.isClickable }
            .map {
                val name = it.label.take(28).ifEmpty { "∅" + it.node.className?.toString()?.substringAfterLast('.').orEmpty() }
                (if (it.visible) "" else "~") + name + (if (it.node.isClickable) "*" else "") +
                    (if (it.withheld > 0) "(${it.withheld} withheld)" else "")
            }.distinct().take(45)
            .joinToString(" | ") +
            " [${items.size} nodes, ${scrollers().size} scrollable, pkgs ${packages.joinToString()}]"

        /**
         * The thing to tap for the Cache row: a tappable element that holds the "Cache" label (or sits in
         * its row, like a trash icon) and nothing from any other row — so it can never be User data.
         */
        fun cacheRowTarget(): Hit? {
            val i = items.indexOfFirst { it.visible && it.label in text.cacheRow }
            if (i < 0) return null
            // Climb to the row: the biggest ancestor whose labels are only "Cache" and sizes.
            var row = i
            var p = items[i].parent
            var d = 0
            while (p >= 0 && d < 4) {
                if (!onlyCacheInside(p)) break
                row = p
                p = items[p].parent
                d++
            }
            // Prefer a tappable icon/button inside the row, else the row itself.
            val inside = items.indices.filter { it != row && isInside(it, row) && items[it].node.isClickable && items[it].visible }
            val pick = inside.lastOrNull() ?: row.takeIf { items[it].node.isClickable } ?: return null
            if (!onlyCacheInside(pick)) return null
            val n = items[pick].node
            return Hit(n, n.isEnabled, items[pick].visible)
        }

        private fun onlyCacheInside(container: Int): Boolean {
            val labels = items.indices.filter { it == container || isInside(it, container) }.map { items[it].label }.filter { it.isNotBlank() }
            return labels.all { it in text.cacheRow || it.any(Char::isDigit) || it.length <= 3 } && labels.any { it in text.cacheRow }
        }

        /** A confirm button in a new dialog that is clearly about cache, never storage or data. */
        fun findSafeCacheConfirm(before: Set<Int>): Hit? {
            val fresh = items.filter { it.window !in before && it.visible }
            if (fresh.isEmpty()) return null
            val labels = fresh.map { it.label }
            if (labels.any { matches(it, text.forbidden) || it.contains("user data") || it.contains("all data") }) return null
            val aboutCache = labels.any { it.contains("cache") }
            for (i in items.indices) {
                val x = items[i]
                if (x.window in before || !x.visible) continue
                val ok = x.label.contains("cache") && text.clearWords.any { x.label.contains(it) } ||
                    aboutCache && (x.id == "android:id/button1" || matches(x.label, text.cacheConfirm))
                if (!ok || matches(x.label, text.forbidden)) continue
                tappable(i)?.takeIf { it.enabled }?.let { return it }
            }
            return null
        }

        /** A new dialog that mentions clearing storage/data rather than cache. */
        fun dialogLooksDangerous(before: Set<Int>): Boolean {
            val fresh = items.filter { it.window !in before && it.visible && it.label.isNotBlank() }
            if (fresh.isEmpty()) return false
            val hard = fresh.any { matches(it.label, text.forbidden) || it.label.contains("user data") || it.label.contains("all data") }
            val aboutCache = fresh.any { it.label.contains("cache") }
            return hard || (!aboutCache && fresh.any { it.label.contains("storage") || it.label.contains("data") })
        }

        /** A confirm button in a window that appeared after [before] (i.e. a fresh dialog). */
        fun findDialogButton(before: Set<Int>, labels: Set<String>, exclude: Hit): Hit? {
            for (i in items.indices) {
                val x = items[i]
                if (!x.visible || x.window in before) continue
                if (x.id != "android:id/button1" && !matches(x.label, labels)) continue
                if (matches(x.label, text.forbidden)) continue
                val h = tappable(i) ?: continue
                if (h.node != exclude.node && h.enabled) return h
            }
            return null
        }

        fun findConfirm(exclude: Hit): Hit? {
            items.indices.firstOrNull { items[it].visible && items[it].id == "android:id/button1" }?.let { i ->
                val h = tappable(i)
                if (h != null && h.node != exclude.node && h.enabled && !matches(items[i].label, text.forbidden)) return h
            }
            for (i in items.indices) {
                if (!items[i].visible || !matches(items[i].label, text.confirm)) continue
                val h = tappable(i) ?: continue
                if (h.node != exclude.node && h.enabled) return h
            }
            return null
        }

        /** The element itself if tappable, else its nearest tappable parent — never one holding a dangerous button. */
        private fun tappable(i: Int, allowForbidden: Boolean = false): Hit? {
            var c = i
            var depth = 0
            while (c >= 0 && depth < 6) {
                val n = items[c].node
                if (n.isClickable) {
                    if (!allowForbidden && holdsForbidden(c)) return null
                    return Hit(n, n.isEnabled && items[i].node.isEnabled, items[i].visible)
                }
                c = items[c].parent
                depth++
            }
            return null
        }

        private fun holdsForbidden(container: Int): Boolean = items.indices.any { j ->
            j != container && matches(items[j].label, text.forbidden) && isInside(j, container)
        } || matches(items[container].label, text.forbidden)

        private fun isInside(j: Int, container: Int): Boolean {
            var p = items[j].parent
            var d = 0
            while (p >= 0 && d < 8) {
                if (p == container) return true
                p = items[p].parent
                d++
            }
            return false
        }
    }

    /** Whole-label match. Compose can merge a row's texts ("Storage & cache, 12 MB used"), so the first part counts too. */
    private fun matches(label: String, set: Set<String>): Boolean {
        if (label.isEmpty()) return false
        if (label in set) return true
        val first = label.substringBefore(", ").substringBefore('\n')
        return first != label && first in set
    }

    private fun snap(): Screen {
        val items = ArrayList<Item>(256)
        val ids = HashSet<Int>()
        val pkgs = HashSet<String>()
        for (root in roots()) {
            ids += root.windowId
            root.packageName?.let { pkgs += it.toString() }
            val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
            queue += root to -1
            var budget = 1200
            while (queue.isNotEmpty() && budget-- > 0) {
                val (n, parent) = queue.removeFirst()
                val idx = items.size
                val item = Item(n, n.label(), parent, n.viewIdResourceName, root.windowId, n.isVisibleToUser)
                items += item
                for (c in 0 until n.childCount) {
                    val ch = child(n, c)
                    if (ch == null) item.withheld++ else queue += ch to idx
                }
            }
        }
        return Screen(items, ids, pkgs)
    }

    /** App windows only (Settings and its dialogs) — skips the keyboard, status bar and our own banner. */
    private fun roots(): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        runCatching { windows }.getOrNull().orEmpty()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .forEach { w -> rootOf(w)?.let { out += it } }
        if (out.isEmpty()) activeRoot()?.let { out += it }
        return out.filter { it.packageName?.toString() != packageName }
    }

    // Plain lookups on purpose. The bulk "prefetch" variants stop at Android's 50-node batch limit, which
    // silently cut every screen off after 50 elements — Clear cache sits past that on Pixel's storage page.
    private fun activeRoot(): AccessibilityNodeInfo? = rootInActiveWindow

    private fun rootOf(w: AccessibilityWindowInfo): AccessibilityNodeInfo? = w.root

    private fun child(n: AccessibilityNodeInfo, i: Int): AccessibilityNodeInfo? = n.getChild(i)

    private fun AccessibilityNodeInfo.label(): String =
        (text?.takeIf { it.isNotBlank() } ?: contentDescription)?.normalized() ?: ""

    /** Re-checks [block] whenever the screen changes (or every 120 ms) until it returns something. */
    private suspend fun <T> poll(timeoutMs: Long, block: () -> T?): T? {
        val end = System.currentTimeMillis() + timeoutMs
        while (true) {
            runCatching(block).getOrNull()?.let { return it }
            val left = end - System.currentTimeMillis()
            if (left <= 0) return null
            withTimeoutOrNull(minOf(left, 120L)) { changed.receive() }
        }
    }

    // ---------------------------------------------------------------- progress overlay

    @SuppressLint("SetTextI18n")
    private fun showOverlay() {
        if (overlay != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

        val label = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            text = "Starting sweep…"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val stopBtn = TextView(this).apply {
            text = "Stop"
            textSize = 15f
            setTextColor(0xFF003919.toInt())
            setPadding(dp(20), dp(10), dp(20), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0xFF93D69F.toInt())
            }
            setOnClickListener { this@SweepService.stop() }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(32).toFloat()
                setColor(0xF0262B26.toInt())
                setStroke(dp(1), 0x59414941)
            }
            addView(label)
            addView(stopBtn)
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            x = 0
            y = dp(48)
            horizontalMargin = 0.04f
        }
        runCatching {
            wm.addView(card, lp)
            overlay = card
            overlayText = label
        }
    }

    private fun hideOverlay() {
        val v = overlay ?: return
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) }
        overlay = null
        overlayText = null
    }

    companion object {
        private const val MIUI = "com.miui.securitycenter"
        /** Caches smaller than this aren't worth a trip to Settings; they refill instantly anyway. */
        const val MIN_CACHE_BYTES = 64L * 1024

        @Volatile
        var instance: SweepService? = null
            private set
    }
}
