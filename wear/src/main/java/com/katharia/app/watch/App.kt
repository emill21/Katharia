package com.katharia.app.watch

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.katharia.app.AccessKeeper
import com.katharia.app.AppRepo
import com.katharia.app.InstalledApp
import com.katharia.app.Prefs
import com.katharia.app.Release
import com.katharia.app.StepResult
import com.katharia.app.Sweep
import com.katharia.app.SweepMode
import com.katharia.app.SweepService
import com.katharia.app.SweepState
import com.katharia.app.Target
import com.katharia.app.wear.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val PAGES = 3

/** Live setup status, re-checked every second or so while the app is open. */
private data class Access(val service: Boolean, val usage: Boolean, val selfEnable: Boolean)

@Composable
private fun rememberAccess(): Access {
    val context = LocalContext.current
    var a by remember { mutableStateOf(readAccess(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_200)
            a = readAccess(context)
        }
    }
    return a
}

private fun readAccess(c: Context) =
    Access(AppRepo.isServiceEnabled(c), AppRepo.hasUsageAccess(c), AccessKeeper.canSelfEnable(c))

@Composable
fun KathariaWearApp(onReplayTour: () -> Unit, onWhatsNew: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val access = rememberAccess()
    val state by Sweep.state.collectAsStateWithLifecycle()
    var includeSystem by remember { mutableStateOf(prefs.includeSystem) }
    var excluded by remember { mutableStateOf(prefs.excluded) }
    var mode by remember { mutableStateOf(prefs.mode) }
    var skipTiny by remember { mutableStateOf(prefs.skipTiny) }
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    // Reload after each sweep too, so cache sizes are fresh.
    val finished = state is SweepState.Finished
    LaunchedEffect(includeSystem, access.usage, reload, finished) {
        apps = withContext(Dispatchers.IO) { AppRepo.load(context, includeSystem) }
    }

    fun setExcluded(v: Set<String>) { excluded = v; prefs.excluded = v }

    val pager = rememberPagerState { PAGES }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> Page {
                    sweepPage(
                        context, state, access, mode, apps, excluded,
                        onSweep = { list ->
                            SweepService.instance?.start(list, mode)
                        },
                    )
                }
                1 -> AppsPage(apps, excluded, ::setExcluded)
                else -> Page {
                    settingsPage(
                        context, access, mode, includeSystem, skipTiny, prefs,
                        onMode = { mode = it; prefs.mode = it },
                        onSystem = { includeSystem = it; prefs.includeSystem = it },
                        onSkipTiny = { skipTiny = it; prefs.skipTiny = it },
                        onReplayTour = onReplayTour,
                        onWhatsNew = onWhatsNew,
                    )
                }
            }
        }
        PageDots(PAGES, pager.currentPage, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
    }
}

/** One scrolling page with the time on top and a scroll bar on the side. */
@Composable
fun Page(content: ScalingLazyListScope.() -> Unit) {
    val state = rememberScalingLazyListState(initialCenterItemIndex = 0)
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = state) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 28.dp, bottom = 36.dp),
            content = content,
        )
    }
}

// ------------------------------------------------------------------ Sweep

private fun ScalingLazyListScope.sweepPage(
    context: Context,
    state: SweepState,
    access: Access,
    mode: SweepMode,
    apps: List<InstalledApp>?,
    excluded: Set<String>,
    onSweep: (List<Target>) -> Unit,
) {
    val targets = apps?.filter { it.packageName !in excluded }?.map { Target(it.packageName, it.label, it.protected) }
    when (state) {
        is SweepState.Running -> {
            item {
                SweepOrb(progress = (state.index + 1f) / state.total.coerceAtLeast(1), modifier = Modifier.size(110.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.index + 1}/${state.total}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.onSurface)
                        Text("sweeping", fontSize = 11.sp, color = Palette.primary)
                    }
                }
            }
            item { Caption(state.current, color = Palette.onSurface) }
            item {
                Chip(
                    onClick = { SweepService.instance?.stop() },
                    label = { Text("Stop") },
                    icon = { Icon(Icons.Rounded.Stop, null) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        is SweepState.Finished -> {
            item {
                SweepOrb(progress = 1f, modifier = Modifier.size(100.dp)) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Palette.primary, modifier = Modifier.size(40.dp))
                }
            }
            item { PageTitle(if (state.cancelled) "Sweep stopped" else "All clean") }
            item {
                InfoCard(
                    "",
                    listOfNotNull(
                        if (state.mode.forceStop) "Stopped" to "${state.stopped}" else null,
                        if (state.mode.clearCache) "Cache cleared" to "${state.cleared}" else null,
                        state.freedBytes?.takeIf { it > 0 }?.let { "Freed" to bytes(it) },
                        "Took" to "${(state.elapsedMs / 1000).coerceAtLeast(1)} s",
                        state.problems.size.takeIf { it > 0 }?.let { "Couldn't finish" to "$it apps" },
                    ),
                )
            }
            if (state.problems.isNotEmpty()) {
                item { Caption("Missed: " + state.problems.take(4).joinToString { it.target.label } + if (state.problems.size > 4) "…" else "") }
                item {
                    // The sweep log's trace for the first miss, to help with odd watch Settings layouts.
                    val first = state.problems.first()
                    val what = listOfNotNull(
                        if (first.stop == StepResult.NotFound) "force stop" else null,
                        if (first.cache == StepResult.NotFound) "clear cache" else null,
                    ).joinToString(" and ")
                    Caption("${first.target.label}: couldn't find $what. ${first.note.takeLast(160)}")
                }
            }
            item {
                Chip(
                    onClick = { Sweep.dismiss() },
                    label = { Text("Done", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        SweepState.Idle -> {
            val ready = access.service && !targets.isNullOrEmpty()
            item {
                SweepOrb(
                    progress = if (ready) 1f else 0f,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .clickable(enabled = ready) { targets?.let(onSweep) },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.CleaningServices, null, tint = Palette.onSurface, modifier = Modifier.size(28.dp))
                        Text("Sweep", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.onSurface)
                        Text(mode.title, fontSize = 10.sp, color = Palette.primary)
                    }
                }
            }
            item {
                val total = apps?.filter { it.packageName !in excluded }?.sumOf { it.cacheBytes ?: 0L } ?: 0L
                Caption(
                    when {
                        apps == null -> "Finding apps…"
                        targets.isNullOrEmpty() -> "No apps selected. Swipe left to pick some."
                        total > 0 -> "${targets.size} apps · ${bytes(total)} of cache"
                        else -> "${targets.size} apps selected"
                    },
                    color = Palette.onSurface,
                )
            }
            if (!access.service) {
                item {
                    Chip(
                        onClick = { openAccessibility(context) },
                        label = { Text("Turn on Katharia") },
                        secondaryLabel = { Text("Accessibility, one time") },
                        icon = { Icon(Icons.Rounded.Accessibility, null) },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { Caption("Settings › Accessibility › Katharia. If it isn't listed, see the computer steps in Settings › Setup.") }
            } else {
                val last = Prefs(context).history.firstOrNull()
                if (last != null) {
                    item {
                        val ago = android.text.format.DateUtils.getRelativeTimeSpanString(last.time).toString()
                        Caption("Last sweep $ago: ${last.stopped} stopped, ${last.cleared} cleaned" +
                            (if (last.freedBytes > 0) ", ${bytes(last.freedBytes)} freed" else ""))
                    }
                }
            }
        }
    }
}

/** The sweep button: a glass orb with a comet ring, like the icon. */
@Composable
fun SweepOrb(progress: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2
            val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            drawArc(Palette.track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            if (progress > 0f) {
                drawArc(
                    Brush.sweepGradient(listOf(Palette.primary.copy(alpha = 0.15f), Palette.primary)),
                    -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arc,
                    style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
            val r = size.minDimension / 2 - stroke * 2
            drawCircle(
                Brush.verticalGradient(listOf(Palette.primaryContainer.copy(alpha = 0.55f), Palette.primaryContainer.copy(alpha = 0.95f))),
                r,
            )
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), center = Offset(size.width * 0.38f, size.height * 0.36f), radius = r), r)
        }
        content()
    }
}

// ------------------------------------------------------------------ Apps

@Composable
private fun AppsPage(apps: List<InstalledApp>?, excluded: Set<String>, setExcluded: (Set<String>) -> Unit) {
    val context = LocalContext.current
    Page {
        item { PageTitle("Apps") }
        if (apps == null) {
            item { Caption("Loading…") }
            return@Page
        }
        item {
            Caption("${apps.count { it.packageName !in excluded }} of ${apps.size} selected · biggest cache first")
        }
        item {
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                CompactChip(onClick = { setExcluded(emptySet()) }, label = { Text("All") }, colors = ChipDefaults.secondaryChipColors())
                CompactChip(onClick = { setExcluded(apps.map { it.packageName }.toSet()) }, label = { Text("None") }, colors = ChipDefaults.secondaryChipColors())
            }
        }
        items(apps, key = { it.packageName }) { app ->
            val on = app.packageName !in excluded
            val icon = remember(app.packageName) { appIcon(context, app.packageName) }
            ToggleChip(
                checked = on,
                onCheckedChange = { checked ->
                    setExcluded(if (checked) excluded - app.packageName else excluded + app.packageName)
                },
                label = { Text(app.label, maxLines = 1) },
                secondaryLabel = {
                    Text(
                        listOfNotNull(
                            app.cacheBytes?.let { bytes(it) },
                            if (app.protected) "cache only" else null,
                            if (app.system) "system" else null,
                        ).joinToString(" · ").ifEmpty { " " },
                        maxLines = 1,
                    )
                },
                appIcon = { if (icon != null) Image(icon, null, Modifier.size(ChipDefaults.IconSize).clip(CircleShape)) },
                toggleControl = { Icon(ToggleChipDefaults.checkboxIcon(on), if (on) "Selected" else "Not selected") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun appIcon(context: Context, pkg: String): ImageBitmap? =
    runCatching { AppRepo.icon(context, pkg)?.toBitmap(64, 64)?.asImageBitmap() }.getOrNull()

// ------------------------------------------------------------------ Settings

private fun ScalingLazyListScope.settingsPage(
    context: Context,
    access: Access,
    mode: SweepMode,
    includeSystem: Boolean,
    skipTiny: Boolean,
    prefs: Prefs,
    onMode: (SweepMode) -> Unit,
    onSystem: (Boolean) -> Unit,
    onSkipTiny: (Boolean) -> Unit,
    onReplayTour: () -> Unit,
    onWhatsNew: () -> Unit,
) {
    item { PageTitle("Sweep mode") }
    SweepMode.entries.forEach { m ->
        item {
            ToggleChip(
                checked = m == mode,
                onCheckedChange = { onMode(m) },
                label = { Text(m.title) },
                secondaryLabel = { Text(m.blurb, maxLines = 2) },
                toggleControl = { Icon(ToggleChipDefaults.radioIcon(m == mode), null) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    item { PageTitle("Options") }
    item {
        ToggleChip(
            checked = includeSystem,
            onCheckedChange = onSystem,
            label = { Text("System apps") },
            toggleControl = { Switch(checked = includeSystem) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    item {
        ToggleChip(
            checked = skipTiny,
            onCheckedChange = onSkipTiny,
            label = { Text("Skip tiny caches") },
            secondaryLabel = { Text("Under 64 KB") },
            toggleControl = { Switch(checked = skipTiny) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    item { PageTitle("Setup") }
    item {
        InfoCard(
            "",
            listOf(
                "Accessibility" to if (access.service) "On" else "Off",
                "Usage access" to if (access.usage) "On" else "Off (optional)",
                "Auto re-enable" to if (access.selfEnable) "On" else "Off (optional)",
            ),
        )
    }
    if (!access.service) {
        item {
            Chip(
                onClick = { openAccessibility(context) },
                label = { Text("Accessibility settings") },
                icon = { Icon(Icons.Rounded.Accessibility, null) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (!access.usage || !access.selfEnable) {
        item { Caption("From a computer (Wireless debugging), optional:") }
        item { AdbCard(access) }
    }
    item {
        val l = prefs.lifetime
        InfoCard(
            "All time",
            listOf(
                "Sweeps" to "${l.sweeps}",
                "Apps stopped" to "${l.stopped}",
                "Caches cleared" to "${l.cleared}",
                "Space freed" to bytes(l.freedBytes),
            ),
        )
    }
    item {
        Chip(
            onClick = onWhatsNew,
            label = { Text("What's new") },
            icon = { Icon(Icons.Rounded.NewReleases, null) },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    item {
        Chip(
            onClick = onReplayTour,
            label = { Text("Replay setup") },
            icon = { Icon(Icons.Rounded.Replay, null) },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    item { Caption("Katharia for Wear OS ${BuildConfig.VERSION_NAME}\nAdd the Katharia tile to sweep from your tiles.") }
}

@Composable
private fun AdbCard(access: Access) {
    val lines = buildList {
        if (!access.selfEnable) add("Auto re-enable" to "adb shell pm grant com.katharia.app android.permission.WRITE_SECURE_SETTINGS")
        if (!access.usage) add("Cache sizes" to "adb shell appops set com.katharia.app GET_USAGE_STATS allow")
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .background(Palette.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        lines.forEach { (title, cmd) ->
            Text(title, fontSize = 11.sp, color = Palette.primary, fontWeight = FontWeight.Medium)
            Text(cmd, fontSize = 10.sp, color = Palette.onSurface, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
    }
}

fun openAccessibility(context: Context) {
    val tries = listOf(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (i in tries) {
        if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}

fun bytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.0f MB".format(b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "%.0f KB".format(b / (1L shl 10).toDouble())
    else -> "$b B"
}

// ------------------------------------------------------------------ Setup & What's new

@Composable
fun SetupScreen(onDone: () -> Unit) {
    val access = rememberAccess()
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    Page {
        item {
            SweepOrb(progress = (step + 1) / 3f, modifier = Modifier.size(80.dp)) {
                Text("${step + 1}/3", fontSize = 13.sp, color = Palette.onSurface, fontWeight = FontWeight.SemiBold)
            }
        }
        when (step) {
            0 -> {
                item { PageTitle("Katharia") }
                item { Caption("A clean sweep for your watch.", color = Palette.primary) }
                item { Caption("Force stop the apps you pick and clear their cache in one tap, right on your wrist.") }
                item { NextChip("Next") { step = 1 } }
            }
            1 -> {
                item { PageTitle("Turn on Katharia") }
                item {
                    Caption(
                        if (access.service) "Katharia is on. You're set."
                        else "Katharia taps the Force stop and Clear cache buttons in Settings for you, so it needs Accessibility. It never taps Clear data.",
                        color = if (access.service) Palette.primary else Palette.muted,
                    )
                }
                if (!access.service) {
                    item {
                        Chip(
                            onClick = { openAccessibility(context) },
                            label = { Text("Open Accessibility") },
                            icon = { Icon(Icons.Rounded.Accessibility, null) },
                            colors = ChipDefaults.primaryChipColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item { Caption("Not listed on your watch? From a computer with Wireless debugging:") }
                    item {
                        InfoCard("", listOf("1" to "adb shell pm grant com.katharia.app android.permission.WRITE_SECURE_SETTINGS", "2" to "Open Katharia again"))
                    }
                }
                item { NextChip(if (access.service) "Next" else "Skip for now") { step = 2 } }
            }
            else -> {
                item { PageTitle("Ready") }
                item { Caption("Swipe left to choose apps and a sweep mode. Add the Katharia tile to sweep without opening the app.") }
                if (!access.usage) {
                    item { Caption("Optional: to see cache sizes, run\nadb shell appops set com.katharia.app GET_USAGE_STATS allow") }
                }
                item { NextChip("Start") { onDone() } }
            }
        }
    }
}

@Composable
private fun NextChip(text: String, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        colors = ChipDefaults.primaryChipColors(),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

@Composable
fun WhatsNewScreen(releases: List<Release>, onDone: () -> Unit) {
    Page {
        item { PageTitle("What's new") }
        releases.forEach { r ->
            item { Caption("${r.version} · ${r.title}", color = Palette.primary) }
            r.items.forEach { line -> item { Caption("• $line", color = Palette.onSurface) } }
        }
        item { NextChip("Got it", onDone) }
    }
}

