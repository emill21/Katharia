package com.sweepr.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sweepr.app.AppRepo
import com.sweepr.app.InstalledApp
import com.sweepr.app.Prefs
import com.sweepr.app.StepResult
import com.sweepr.app.Sweep
import com.sweepr.app.SweepMode
import com.sweepr.app.SweepService
import com.sweepr.app.SweepState
import com.sweepr.app.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val haptics = LocalHapticFeedback.current

    var serviceOn by remember { mutableStateOf(AppRepo.isServiceEnabled(ctx)) }
    var usageOn by remember { mutableStateOf(AppRepo.hasUsageAccess(ctx)) }
    var includeSystem by remember { mutableStateOf(prefs.includeSystem) }
    var excluded by remember { mutableStateOf(prefs.excluded) }
    var mode by remember { mutableStateOf(prefs.mode) }
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }

    val state by Sweep.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        serviceOn = AppRepo.isServiceEnabled(ctx)
        usageOn = AppRepo.hasUsageAccess(ctx)
        reload++
        onPauseOrDispose { }
    }
    LaunchedEffect(includeSystem, reload) {
        apps = withContext(Dispatchers.IO) { AppRepo.load(ctx, includeSystem) }
    }

    val all = apps.orEmpty()
    val selected = all.filter { it.packageName !in excluded }
    val shown = if (query.isBlank()) all else all.filter {
        it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
    }
    val selectedCache = if (usageOn) selected.sumOf { it.cacheBytes ?: 0L } else null
    val running = state is SweepState.Running
    val colors = MaterialTheme.colorScheme

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Box(Modifier.fillMaxSize().background(colors.surface)) {
        AnimatedBackground("aurora", Modifier.fillMaxSize())
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            contentColor = colors.onSurface,
            topBar = {
                LargeTopAppBar(
                    title = { Text("Sweepr", style = MaterialTheme.typography.headlineLarge) },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = colors.surfaceContainer,
                    ),
                )
            },
            bottomBar = {
                FloatingSweepBar(
                    count = selected.size,
                    cache = if (mode.clearCache) selectedCache else null,
                    running = running,
                    enabled = serviceOn && selected.isNotEmpty(),
                    onSweep = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        confirm = true
                    },
                    onStop = { SweepService.instance?.stop() },
                )
            },
        ) { padding ->
            LazyColumn(
                contentPadding = padding.withPage(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                animatedItem(0) {
                    AnimatedContent(
                        targetState = state,
                        contentKey = { it::class },
                        transitionSpec = {
                            (fadeIn(tween(260, delayMillis = 60)) + scaleIn(tween(260, delayMillis = 60), initialScale = 0.97f))
                                .togetherWith(fadeOut(tween(90)))
                        },
                        label = "hero",
                    ) { s ->
                        when (s) {
                            is SweepState.Running -> RunningHero(s)
                            is SweepState.Finished -> ResultHero(s) { Sweep.dismiss() }
                            SweepState.Idle -> ReadyHero(
                                count = selected.size,
                                total = all.size,
                                cache = selectedCache,
                                mode = mode,
                                onMode = {
                                    if (it != mode) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    mode = it; prefs.mode = it
                                },
                            )
                        }
                    }
                }
                if (!serviceOn) animatedItem(1) { ServiceCard(ctx) }
                if (!usageOn) animatedItem(2) { UsageCard(ctx) }

                animatedItem(3) { SectionHeader("Apps") }
                animatedItem(3) {
                    SearchField(query) { query = it }
                }
                animatedItem(4) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = includeSystem,
                            onClick = { includeSystem = !includeSystem; prefs.includeSystem = includeSystem },
                            label = { Text("System apps") },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(containerColor = colors.surfaceContainerLow),
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            excluded = excluded - shown.map { it.packageName }.toSet(); prefs.excluded = excluded
                        }) { Text("Select all") }
                        TextButton(onClick = {
                            excluded = excluded + shown.map { it.packageName }; prefs.excluded = excluded
                        }) { Text("None") }
                    }
                }
                if (includeSystem) animatedItem(4) {
                    Text(
                        "Force-stopping system apps can briefly drop things like your keyboard, launcher or Bluetooth until they restart.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }

                if (apps == null) {
                    item {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (shown.isEmpty()) {
                    item {
                        SectionCard {
                            Text(
                                "No apps match “$query”.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                itemsIndexed(shown, key = { _, a -> a.packageName }) { i, app ->
                    Entrance(minOf(i + 5, 8)) {
                        AppRow(
                            app = app,
                            checked = app.packageName !in excluded,
                            first = i == 0,
                            last = i == shown.lastIndex,
                            onToggle = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                excluded = if (app.packageName in excluded) excluded - app.packageName else excluded + app.packageName
                                prefs.excluded = excluded
                            },
                        )
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Rounded.AutoAwesome, null) },
            title = { Text("Sweep ${selected.size} apps?") },
            text = {
                Text(
                    "Your screen will flip through Settings for each app, about 2–4 seconds each. " +
                        "Don't touch the phone until it's done. Tap Stop on the banner to cancel."
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirm = false
                    val svc = SweepService.instance
                    if (svc == null) serviceOn = false
                    else svc.start(selected.map { Target(it.packageName, it.label) }, mode)
                }) { Text("Start") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- heroes

@Composable
private fun ReadyHero(count: Int, total: Int, cache: Long?, mode: SweepMode, onMode: (SweepMode) -> Unit) {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme
    GradientCard(top = colors.primaryContainer.copy(alpha = 0.8f), bottom = colors.surfaceContainerLow) {
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.CleaningServices, colors.primary, size = 56.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Ready to sweep", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    BigNumber(count.toString(), if (count == 1) "app" else "apps")
                }
            }
            Spacer(Modifier.height(18.dp))
            HeroStatStrip(
                listOf(
                    HeroStat(Icons.Rounded.Apps, "Selected", "$count of $total"),
                    HeroStat(Icons.Rounded.Storage, "Cache", cache?.let { Formatter.formatShortFileSize(ctx, it) } ?: "—"),
                    HeroStat(Icons.Rounded.AutoAwesome, "Action", mode.label),
                ),
                container = colors.surfaceContainerLowest.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(16.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SweepMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = m == mode,
                        onClick = { onMode(m) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = SweepMode.entries.size),
                    ) { Text(m.label, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun RunningHero(s: SweepState.Running) {
    val colors = MaterialTheme.colorScheme
    val progress by animateFloatAsState((s.index + 1f) / s.total.coerceAtLeast(1), tween(500), label = "sweep")
    GradientCard(top = colors.primaryContainer.copy(alpha = 0.8f), bottom = colors.surfaceContainerLow) {
        Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                val track = colors.primary.copy(alpha = 0.14f)
                val arc = colors.primary
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 14.dp.toPx()
                    val tl = Offset(stroke / 2, stroke / 2)
                    val sz = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(stroke))
                    drawArc(arc, -90f, 360f * progress, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RollingText("${s.index + 1}", style = MaterialTheme.typography.headlineMedium)
                    Text("of ${s.total}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Sweeping", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                Text(s.current, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    when (s.mode) {
                        SweepMode.Both -> "Force stop + clear cache"
                        SweepMode.CacheOnly -> "Clearing cache"
                        SweepMode.StopOnly -> "Force stopping"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ResultHero(s: SweepState.Finished, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val skipped = s.outcomes.count { it.stop != StepResult.Done && it.cache != StepResult.Done && it !in s.problems }
    GradientCard(top = colors.tertiaryContainer.copy(alpha = 0.8f), bottom = colors.surfaceContainerLow) {
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(if (s.cancelled) Icons.Rounded.StopCircle else Icons.Rounded.CheckCircle, colors.tertiary, size = 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (s.cancelled) "Sweep stopped" else "All clean", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${s.outcomes.size} apps checked" + if (skipped > 0) " · $skipped already clean" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Dismiss") }
            }
            Spacer(Modifier.height(18.dp))
            HeroStatStrip(
                buildList {
                    if (s.mode.forceStop) add(HeroStat(Icons.Rounded.StopCircle, "Stopped", "${s.stopped}"))
                    if (s.mode.clearCache) add(HeroStat(Icons.Rounded.CleaningServices, "Cache cleared", "${s.cleared}"))
                    if (s.mode.clearCache) add(
                        HeroStat(Icons.Rounded.Storage, "Freed", s.freedBytes?.let { Formatter.formatShortFileSize(ctx, it) } ?: "—")
                    )
                },
                container = colors.surfaceContainerLowest.copy(alpha = 0.6f),
            )
            if (s.problems.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = colors.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Couldn't finish ${s.problems.size}: " + s.problems.take(5).joinToString { it.target.label } +
                            if (s.problems.size > 5) "…" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- setup cards

@Composable
private fun SetupCard(
    icon: ImageVector,
    title: String,
    body: String,
    actions: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    GradientCard(top = colors.secondaryContainer.copy(alpha = 0.8f), bottom = colors.surfaceContainerLow) {
        Column(Modifier.padding(24.dp)) {
            IconBadge(icon, colors.primary, size = 48.dp)
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Stays on your phone. Nothing is uploaded.", style = MaterialTheme.typography.labelLarge, color = colors.primary)
            }
            Spacer(Modifier.height(18.dp))
            actions()
        }
    }
}

@Composable
private fun ServiceCard(ctx: Context) {
    val restricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    SetupCard(
        Icons.Rounded.Shield,
        "Turn on Sweepr",
        "Android only lets apps force-stop and clear cache for other apps by tapping the buttons in Settings. " +
            "Switch on Sweepr under Accessibility → Installed apps so it can do that for you." +
            if (restricted) "\n\nSwitch greyed out? Tap App info → ⋮ menu → Allow restricted settings, then try again." else "",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { ctx.open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Open Accessibility") }
            if (restricted) OutlinedButton(onClick = {
                ctx.open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
            }) { Text("App info") }
        }
    }
}

@Composable
private fun UsageCard(ctx: Context) {
    SetupCard(
        Icons.Rounded.Storage,
        "See cache sizes",
        "Optional. Allow “Usage access” to see how much cache each app is holding and how much a sweep frees.",
    ) {
        FilledTonalButton(onClick = { ctx.open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Allow usage access") }
    }
}

// ---------------------------------------------------------------- list

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    TextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search apps") },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "Clear") }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerHigh,
            unfocusedContainerColor = colors.surfaceContainerLow,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/** One app, drawn as part of a grouped list: big rounding at the ends, small in between. */
@Composable
private fun AppRow(app: InstalledApp, checked: Boolean, first: Boolean, last: Boolean, onToggle: () -> Unit) {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val big = 28.dp
    val small = 6.dp
    val shape = RoundedCornerShape(
        topStart = if (first) big else small, topEnd = if (first) big else small,
        bottomStart = if (last) big else small, bottomEnd = if (last) big else small,
    )
    val bg by animateColorAsState(
        if (checked) colors.surfaceContainerLow else colors.surfaceContainerLowest.copy(alpha = 0.5f),
        tween(200), label = "row",
    )
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .padding(top = 0.dp)
            .pressScale(interaction, 0.98f)
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onToggle)
            .padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(ctx, app)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildString {
                app.cacheBytes?.let { append(Formatter.formatShortFileSize(ctx, it)).append(" cache") }
                if (app.system) { if (isNotEmpty()) append(" · "); append("System") }
            }
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
    Spacer(Modifier.height(if (last) 0.dp else 2.dp))
}

@Composable
private fun AppIcon(ctx: Context, app: InstalledApp) {
    val colors = MaterialTheme.colorScheme
    val bmp by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            AppRepo.icon(ctx, app.packageName)?.let { runCatching { it.toBitmap(132, 132).asImageBitmap() }.getOrNull() }
        }
    }
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (bmp == null) colors.primary.copy(alpha = 0.14f) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize())
        else Text(app.label.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = colors.primary)
    }
}

// ---------------------------------------------------------------- floating bar

/** Floating pill at the bottom, styled like Bataria's nav bar, holding the main action. */
@Composable
private fun FloatingSweepBar(
    count: Int,
    cache: Long?,
    running: Boolean,
    enabled: Boolean,
    onSweep: () -> Unit,
    onStop: () -> Unit,
) {
    val ctx = LocalContext.current
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
            val interaction = remember { MutableInteractionSource() }
            Button(
                onClick = if (running) onStop else onSweep,
                enabled = running || enabled,
                interactionSource = interaction,
                modifier = Modifier.padding(6.dp).pressScale(interaction, 0.94f).height(56.dp),
                colors = if (running) ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
                else ButtonDefaults.buttonColors(),
            ) {
                Icon(if (running) Icons.Rounded.StopCircle else Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(10.dp))
                val extra = if (cache != null && cache > 0) " · ${Formatter.formatShortFileSize(ctx, cache)}" else ""
                Text(
                    if (running) "Stop sweep" else "Sweep $count ${if (count == 1) "app" else "apps"}$extra",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

private fun Context.open(i: Intent) {
    runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
