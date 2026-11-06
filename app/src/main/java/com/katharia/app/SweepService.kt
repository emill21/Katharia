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
            needCache -> tapClearCache(knownCache = cacheNow).also { mark("cache:$it") }
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

    private suspend fun tapClearCache(knownCache: Long?): StepResult {
        var clear = snap().find(text.clearCache) // a few skins show it right on App info
        if (clear == null) {
            val storage = findWithScroll(text.storage) ?: return StepResult.NotFound
            storage.click()
            clear = poll(4000) { snap().find(text.clearCache) } ?: return StepResult.NotFound
        }
        // Settings greys the button out until it has measured the cache.
        if (!clear.enabled) {
            clear = poll(if (knownCache != null) 2500 else 1000) { snap().find(text.clearCache)?.takeIf { it.enabled } }
                ?: return StepResult.AlreadyClean
        }
        clear.click() // returns once Settings has handled the tap
        delay(40)
        return StepResult.Done
    }

    private suspend fun findWithScroll(labels: Set<String>): Hit? {
        repeat(6) {
            val sc = snap()
            sc.find(labels)?.let { return it }
            val scroller = sc.items.firstOrNull { it.node.isScrollable } ?: return poll(1200) { snap().find(labels) }
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

    private class Item(val node: AccessibilityNodeInfo, val label: String, val parent: Int, val id: String?)

    inner class Hit(val node: AccessibilityNodeInfo, val enabled: Boolean) {
        fun click() = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private inner class Screen(val items: List<Item>, val windowIds: Set<Int>) {
        fun hasText(s: String) = items.any { it.label.contains(s) }

        /** First visible element whose label is one of [labels], resolved to the thing you'd tap. */
        fun find(labels: Set<String>): Hit? {
            for (i in items.indices) if (matches(items[i].label, labels)) tappable(i)?.let { return it }
            return null
        }

        fun findConfirm(exclude: Hit): Hit? {
            items.indices.firstOrNull { items[it].id == "android:id/button1" }?.let { i ->
                val h = tappable(i)
                if (h != null && h.node != exclude.node && h.enabled && !matches(items[i].label, text.forbidden)) return h
            }
            for (i in items.indices) {
                if (!matches(items[i].label, text.confirm)) continue
                val h = tappable(i) ?: continue
                if (h.node != exclude.node && h.enabled) return h
            }
            return null
        }

        /** The element itself if tappable, else its nearest tappable parent — never one holding a dangerous button. */
        private fun tappable(i: Int): Hit? {
            var c = i
            var depth = 0
            while (c >= 0 && depth < 6) {
                val n = items[c].node
                if (n.isClickable) {
                    if (holdsForbidden(c)) return null
                    return Hit(n, n.isEnabled && items[i].node.isEnabled)
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
        for (root in roots()) {
            ids += root.windowId
            val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
            queue += root to -1
            var budget = 900
            while (queue.isNotEmpty() && budget-- > 0) {
                val (n, parent) = queue.removeFirst()
                if (parent >= 0 && !n.isVisibleToUser) continue
                val idx = items.size
                items += Item(n, n.label(), parent, n.viewIdResourceName)
                for (c in 0 until n.childCount) child(n, c)?.let { queue += it to idx }
            }
        }
        return Screen(items, ids)
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

    private fun activeRoot(): AccessibilityNodeInfo? =
        if (Build.VERSION.SDK_INT >= 33) getRootInActiveWindow(AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_HYBRID)
        else rootInActiveWindow

    private fun rootOf(w: AccessibilityWindowInfo): AccessibilityNodeInfo? =
        if (Build.VERSION.SDK_INT >= 33) w.getRoot(AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_HYBRID) else w.root

    private fun child(n: AccessibilityNodeInfo, i: Int): AccessibilityNodeInfo? =
        if (Build.VERSION.SDK_INT >= 33) n.getChild(i, AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_HYBRID) else n.getChild(i)

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
        /** Caches smaller than this aren't worth a trip to Settings; they refill instantly anyway. */
        const val MIN_CACHE_BYTES = 64L * 1024

        @Volatile
        var instance: SweepService? = null
            private set
    }
}
