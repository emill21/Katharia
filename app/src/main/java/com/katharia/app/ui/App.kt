package com.katharia.app.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.katharia.app.AccessKeeper
import com.katharia.app.AppRepo
import com.katharia.app.BuildConfig
import com.katharia.app.Changelog
import com.katharia.app.InstalledApp
import com.katharia.app.Look
import com.katharia.app.Prefs
import com.katharia.app.SortBy
import com.katharia.app.Sweep
import com.katharia.app.SweepMode
import com.katharia.app.SweepService
import com.katharia.app.SweepState
import com.katharia.app.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class KTab(val label: String, val title: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Sweep("Sweep", "Katharia", Icons.Outlined.AutoAwesome, Icons.Rounded.AutoAwesome),
    Apps("Apps", "Apps", Icons.Outlined.Apps, Icons.Rounded.Apps),
    Insights("Insights", "Insights", Icons.Outlined.Insights, Icons.Rounded.Insights),
    Settings("Settings", "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

/** Everything the tabs share: the app list, the selection, the chosen action and the access switches. */
@Stable
class KathariaState(private val ctx: Context) {
    val prefs = Prefs(ctx)

    var serviceOn by mutableStateOf(AppRepo.isServiceEnabled(ctx))
    var usageOn by mutableStateOf(AppRepo.hasUsageAccess(ctx))
    var selfHeal by mutableStateOf(AccessKeeper.canSelfEnable(ctx))
    var includeSystem by mutableStateOf(prefs.includeSystem); private set
    var excluded by mutableStateOf(prefs.excluded); private set
    var mode by mutableStateOf(prefs.mode); private set
    var sort by mutableStateOf(prefs.sort); private set
    var skipTiny by mutableStateOf(prefs.skipTiny); private set
    var apps by mutableStateOf<List<InstalledApp>?>(null)
    var reload by mutableIntStateOf(0)
    var lifetime by mutableStateOf(prefs.lifetime)
    var history by mutableStateOf(prefs.history)
    var burst by mutableIntStateOf(0)
    var confirm by mutableStateOf(false)

    val all: List<InstalledApp> get() = apps.orEmpty()
    val selected: List<InstalledApp> get() = all.filter { it.packageName !in excluded }
    val selectedCache: Long? get() = if (usageOn) selected.sumOf { it.cacheBytes ?: 0L } else null
    val runningCount: Int get() = selected.count { it.active && !it.protected }
    val maxCache: Long get() = all.maxOfOrNull { it.cacheBytes ?: 0L } ?: 0L

    fun isSelected(pkg: String) = pkg !in excluded
    fun toggle(pkg: String) { excluded = if (pkg in excluded) excluded - pkg else excluded + pkg; prefs.excluded = excluded }
    fun selectAll(list: List<InstalledApp>) { excluded = excluded - list.map { it.packageName }.toSet(); prefs.excluded = excluded }
    fun selectNone(list: List<InstalledApp>) { excluded = excluded + list.map { it.packageName }; prefs.excluded = excluded }
    fun chooseMode(m: SweepMode) { mode = m; prefs.mode = m }
    fun chooseSort(s: SortBy) { sort = s; prefs.sort = s }
    fun setSystem(on: Boolean) { includeSystem = on; prefs.includeSystem = on }
    fun chooseSkipTiny(on: Boolean) { skipTiny = on; prefs.skipTiny = on }

    fun refreshAccess() {
        AccessKeeper.ensureEnabled(ctx)
        serviceOn = AppRepo.isServiceEnabled(ctx)
        usageOn = AppRepo.hasUsageAccess(ctx)
        selfHeal = AccessKeeper.canSelfEnable(ctx)
    }

    fun refreshStats() {
        lifetime = prefs.lifetime
        history = prefs.history
    }

    fun canSweep(): Boolean = serviceOn && selected.isNotEmpty() && (mode != SweepMode.Running || runningCount > 0)

    fun startSweep() {
        val svc = SweepService.instance
        if (svc == null) { serviceOn = false; return }
        svc.start(selected.map { Target(it.packageName, it.label, it.protected) }, mode)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KathariaApp(onReplayTour: () -> Unit) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val st = remember { KathariaState(ctx) }
    var tab by rememberSaveable { mutableStateOf(KTab.Sweep) }
    val state by Sweep.state.collectAsStateWithLifecycle()
    val look by Look.state.collectAsStateWithLifecycle()
    var logFor by remember { mutableStateOf<SweepState.Finished?>(null) }

    LifecycleResumeEffect(Unit) {
        st.refreshAccess()
        st.reload++
        onPauseOrDispose { }
    }
    LaunchedEffect(st.includeSystem, st.reload) {
        st.apps = withContext(Dispatchers.IO) { AppRepo.load(ctx, st.includeSystem) }
    }
    // A sweep landed: refresh numbers, jump to the Sweep tab and celebrate.
    LaunchedEffect(state) {
        val s = state
        if (s is SweepState.Finished) {
            st.refreshStats()
            st.reload++
            tab = KTab.Sweep
            if (!s.cancelled) {
                st.burst++
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    // "What's new" once after each update (not on a fresh install).
    val releases = remember { Changelog.load(ctx) }
    var showWhatsNew by remember {
        mutableStateOf(st.prefs.lastSeenVersion < BuildConfig.VERSION_CODE && (st.prefs.lastSeenVersion > 0 || st.prefs.lifetime.sweeps > 0))
    }
    var showChangelog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!showWhatsNew) st.prefs.lastSeenVersion = BuildConfig.VERSION_CODE }

    val colors = MaterialTheme.colorScheme
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Box(Modifier.fillMaxSize().background(colors.surface)) {
        AnimatedBackground(look.background, Modifier.fillMaxSize())
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            contentColor = colors.onSurface,
            topBar = {
                LargeTopAppBar(
                    title = { Text(tab.title, style = MaterialTheme.typography.headlineLarge) },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = colors.surfaceContainer,
                    ),
                )
            },
            bottomBar = {
                FloatingNavBar(tab) { t ->
                    if (t != tab) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    tab = t
                }
            },
        ) { padding ->
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(tween(260, delayMillis = 60)) + scaleIn(tween(260, delayMillis = 60), initialScale = 0.97f))
                        .togetherWith(fadeOut(tween(90)))
                },
                label = "tabs",
            ) { t ->
                when (t) {
                    KTab.Sweep -> SweepScreen(
                        st, state, padding,
                        onOpenApps = { tab = KTab.Apps },
                        onOpenInsights = { tab = KTab.Insights },
                        onLog = { logFor = it },
                    )
                    KTab.Apps -> AppsScreen(st, padding)
                    KTab.Insights -> InsightsScreen(st, padding)
                    KTab.Settings -> SettingsScreen(
                        st, look, padding,
                        onReplayTour = onReplayTour,
                        onChangelog = { showChangelog = true },
                    )
                }
            }
        }
    }

    if (st.confirm) {
        val selected = st.selected
        val guarded = selected.count { it.protected }
        AlertDialog(
            onDismissRequest = { st.confirm = false },
            icon = { Icon(Icons.Rounded.AutoAwesome, null) },
            title = { Text(if (st.mode == SweepMode.Running) "Close ${st.runningCount} running apps?" else "Sweep ${selected.size} apps?") },
            text = {
                Text(
                    "Your screen will flip through Settings for each app that needs it, about a second each. " +
                        "Apps that are already stopped or clean are skipped instantly. " +
                        "Hands off until it's done; tap Stop on the banner to cancel." +
                        if (guarded > 0 && st.mode.forceStop) "\n\n$guarded protected apps (keyboard, launcher…) won't be force-stopped." else ""
                )
            },
            confirmButton = {
                Button(onClick = { st.confirm = false; st.startSweep() }) { Text("Start") }
            },
            dismissButton = { TextButton(onClick = { st.confirm = false }) { Text("Cancel") } },
        )
    }
    logFor?.let { SweepLogSheet(it) { logFor = null } }
    if (showWhatsNew && releases.isNotEmpty()) {
        ChangelogSheet(releases, latestOnly = true) {
            st.prefs.lastSeenVersion = BuildConfig.VERSION_CODE
            showWhatsNew = false
        }
    }
    if (showChangelog) ChangelogSheet(releases, latestOnly = false) { showChangelog = false }
}

/** Bataria's floating pill navigation: the selected tab expands to show its label. */
@Composable
fun FloatingNavBar(tab: KTab, onTab: (KTab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(colors.surface.copy(alpha = 0f), colors.surface.copy(alpha = 0.72f))))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = colors.surfaceContainerHigh.let { if (it.alpha > 0.82f) it.copy(alpha = 0.82f) else it },
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f)),
        ) {
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                KTab.entries.forEach { t -> NavPill(t, selected = t == tab) { onTab(t) } }
            }
        }
    }
}

@Composable
private fun NavPill(t: KTab, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) colors.primary else Color.Transparent, tween(250), label = "navBg")
    val fg by animateColorAsState(if (selected) colors.onPrimary else colors.onSurfaceVariant, tween(250), label = "navFg")
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .pressScale(interaction, 0.9f)
            .clip(CircleShape)
            .background(bg)
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .animateContentSize(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (selected) t.selectedIcon else t.icon, contentDescription = t.label, tint = fg)
        if (selected) {
            Text(t.label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
