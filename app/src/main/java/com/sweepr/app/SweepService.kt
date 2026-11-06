package com.sweepr.app

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
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit // we poll instead
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
            var before: Long? = null
            var cancelled = false
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
                Sweep.set(SweepState.Running(mode, 0, list.size, list.firstOrNull()?.label ?: ""))
                before = if (mode.clearCache) withContext(Dispatchers.IO) { AppRepo.totalCache(this@SweepService, list) } else null
                showOverlay()
                list.forEachIndexed { i, t ->
                    Sweep.set(SweepState.Running(mode, i, list.size, t.label, outcomes.takeLast(4).reversed(), before))
                    overlayText?.text = "Sweeping ${i + 1} of ${list.size}\n${t.label}"
                    outcomes += sweepOne(t, mode)
                }
            } catch (e: CancellationException) {
                cancelled = true
            } finally {
                withContext(NonCancellable) {
                    hideOverlay()
                    val after = if (before != null) withContext(Dispatchers.IO) { AppRepo.totalCache(this@SweepService, list) } else null
                    val freed = if (before != null && after != null) (before - after).coerceAtLeast(0) else null
                    val done = SweepState.Finished(mode, outcomes.toList(), cancelled, freed)
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

    private suspend fun sweepOne(t: Target, mode: SweepMode): AppOutcome {
        openAppInfo(t.packageName)
        // Wait for this app's page (its name shows in the header) so we never act on the previous one.
        poll(3500) { if (anyRootHasText(t.label)) true else null }
        val ready = poll(4000) { findTappable(text.forceStop) ?: findTappable(text.storage) }
        if (ready == null) return AppOutcome(t, notFoundIf(mode.forceStop), notFoundIf(mode.clearCache))

        val stop = if (mode.forceStop && !t.protect) tapForceStop() else StepResult.Skipped
        val cache = if (mode.clearCache) tapClearCache() else StepResult.Skipped
        return AppOutcome(t, stop, cache)
    }

    private fun notFoundIf(b: Boolean) = if (b) StepResult.NotFound else StepResult.Skipped

    private suspend fun tapForceStop(): StepResult {
        val btn = poll(2500) { findTappable(text.forceStop) } ?: return StepResult.NotFound
        if (!btn.isEnabled) return StepResult.AlreadyClean // already stopped, or a protected app
        btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        val ok = poll(2500) { findConfirm(exclude = btn) }
        if (ok != null) {
            ok.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(250)
            return StepResult.Done
        }
        // Some skins stop the app straight away with no dialog; the button greys out.
        val greyed = poll(1200) { findTappable(text.forceStop)?.takeIf { !it.isEnabled } }
        return if (greyed != null) StepResult.Done else StepResult.NotFound
    }

    private suspend fun tapClearCache(): StepResult {
        var clear = findTappable(text.clearCache) // a few skins show it right on App info
        if (clear == null) {
            val storage = findWithScroll(text.storage) ?: return StepResult.NotFound
            storage.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            clear = poll(4500) { findTappable(text.clearCache) } ?: return StepResult.NotFound
        }
        // Settings greys the button out until it has measured the cache; give it a moment.
        if (!clear.isEnabled) {
            clear = poll(1500) { findTappable(text.clearCache)?.takeIf { it.isEnabled } }
                ?: return StepResult.AlreadyClean
        }
        clear.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        delay(300)
        return StepResult.Done
    }

    private fun openAppInfo(pkg: String) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or
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

    // ---------------------------------------------------------------- finding buttons

    private fun roots(): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { list += it }
        runCatching { windows }.getOrNull()?.forEach { w -> w.root?.let { if (it != list.firstOrNull()) list += it } }
        // Ignore our own overlay.
        return list.filter { it.packageName?.toString() != packageName }
    }

    private fun anyRootHasText(s: String): Boolean =
        roots().any { r -> r.findAccessibilityNodeInfosByText(s).orEmpty().any { it.isVisibleToUser } }

    /** Finds a visible element whose whole label is one of [labels], and returns the thing to tap on. */
    private fun findTappable(labels: Set<String>): AccessibilityNodeInfo? {
        for (root in roots()) {
            val hit = bfs(root) { n -> n.isVisibleToUser && n.label() in labels } ?: continue
            return tappableFor(hit)
        }
        return null
    }

    private fun findConfirm(exclude: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (root in roots()) {
            root.findAccessibilityNodeInfosByViewId("android:id/button1").orEmpty()
                .firstOrNull { it.isVisibleToUser && it.isEnabled && it != exclude && it.label() !in text.forbidden }
                ?.let { return it }
        }
        for (root in roots()) {
            val hit = bfs(root) { n -> n.isVisibleToUser && n.label() in text.confirm } ?: continue
            val t = tappableFor(hit) ?: continue
            if (t != exclude && t.isEnabled) return t
        }
        return null
    }

    /** The label itself if it's tappable, otherwise the nearest tappable parent row — never one that also holds a dangerous button. */
    private fun tappableFor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 5) {
            if (n.isClickable) return if (subtreeHasForbidden(n)) null else n
            n = n.parent
            depth++
        }
        return null
    }

    private fun subtreeHasForbidden(node: AccessibilityNodeInfo): Boolean =
        bfs(node, limit = 60) { it.label() in text.forbidden } != null

    private suspend fun findWithScroll(labels: Set<String>): AccessibilityNodeInfo? {
        repeat(6) {
            findTappable(labels)?.let { return it }
            val scroller = roots().firstNotNullOfOrNull { r -> bfs(r) { it.isScrollable && it.isVisibleToUser } }
                ?: return poll(1500) { findTappable(labels) }
            scroller.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            delay(350)
        }
        return findTappable(labels)
    }

    private fun AccessibilityNodeInfo.label(): String =
        (text ?: contentDescription)?.normalized() ?: ""

    private fun bfs(
        root: AccessibilityNodeInfo,
        limit: Int = 800,
        match: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue += root
        var seen = 0
        while (queue.isNotEmpty() && seen < limit) {
            val n = queue.removeFirst()
            seen++
            if (match(n)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue += it }
        }
        return null
    }

    private suspend fun <T> poll(timeoutMs: Long, block: () -> T?): T? {
        val end = System.currentTimeMillis() + timeoutMs
        while (true) {
            runCatching(block).getOrNull()?.let { return it }
            if (System.currentTimeMillis() > end) return null
            delay(100)
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
        @Volatile
        var instance: SweepService? = null
            private set
    }
}
