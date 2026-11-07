package com.katharia.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TouchApp
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
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.katharia.app.AppOutcome
import com.katharia.app.AppRepo
import com.katharia.app.InstalledApp
import com.katharia.app.Lifetime
import com.katharia.app.Look
import com.katharia.app.Prefs
import com.katharia.app.SortBy
import com.katharia.app.StepResult
import com.katharia.app.Sweep
import com.katharia.app.SweepMode
import com.katharia.app.SweepService
import com.katharia.app.SweepState
import com.katharia.app.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

internal const val FULL_ORB_BYTES = 3L * 1024 * 1024 * 1024 // 3 GB of cache fills the orb

// ---------------------------------------------------------------- hero

@Composable
internal fun Hero(
    state: SweepState,
    count: Int,
    runningCount: Int,
    total: Int,
    cache: Long?,
    mode: SweepMode,
    burst: Int,
    onDismiss: () -> Unit,
    onLog: () -> Unit = {},
    idleBody: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme

    fun levelFor(bytes: Long?): Float =
        if (bytes != null) sqrt((bytes.toDouble() / FULL_ORB_BYTES).coerceIn(0.0, 1.0)).toFloat().coerceAtLeast(0.08f)
        else if (total > 0) 0.15f + 0.7f * count / total else 0.3f

    val level = when (state) {
        SweepState.Idle -> levelFor(cache)
        is SweepState.Running -> {
            val start = levelFor(state.startCache ?: cache)
            if (state.mode.clearCache) start * (1f - state.index.toFloat() / state.total.coerceAtLeast(1)) else start
        }
        is SweepState.Finished -> if (state.mode.clearCache && !state.cancelled) 0.04f else levelFor(cache)
    }
    val progress = (state as? SweepState.Running)?.let { (it.index + 1f) / it.total.coerceAtLeast(1) }
    val top = if (state is SweepState.Finished) colors.tertiaryContainer else colors.primaryContainer

    GradientCard(top = top.copy(alpha = 0.8f), bottom = colors.surfaceContainerLow) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (state is SweepState.Finished) {
                Box(Modifier.fillMaxWidth()) {
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Rounded.Close, "Dismiss")
                    }
                }
            }
            CacheOrb(
                level = level,
                progress = progress,
                spinning = state is SweepState.Running,
                burst = burst,
                modifier = Modifier.size(240.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when (state) {
                        SweepState.Idle -> if (mode == SweepMode.Running) {
                            RollingText("$runningCount", style = MaterialTheme.typography.displaySmall)
                            OrbLabel("running now")
                        } else if (cache != null) {
                            RollingText(Formatter.formatShortFileSize(ctx, cache), style = MaterialTheme.typography.headlineLarge)
                            OrbLabel("cache to clear")
                        } else {
                            RollingText("$count", style = MaterialTheme.typography.displaySmall)
                            OrbLabel(if (count == 1) "app selected" else "apps selected")
                        }
                        is SweepState.Running -> {
                            RollingText("${state.index + 1}/${state.total}", style = MaterialTheme.typography.headlineLarge)
                            OrbLabel("sweeping")
                        }
                        is SweepState.Finished -> {
                            val freed = state.freedBytes
                            if (freed != null && freed > 0) {
                                CountUpBytes(freed)
                                OrbLabel("freed")
                            } else {
                                RollingText("${state.outcomes.size}", style = MaterialTheme.typography.displaySmall)
                                OrbLabel("apps swept")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = {
                    (fadeIn(tween(260, delayMillis = 60)) + scaleIn(tween(260, delayMillis = 60), initialScale = 0.97f))
                        .togetherWith(fadeOut(tween(90)))
                },
                label = "heroBody",
            ) { s ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (s) {
                        SweepState.Idle -> idleBody()
                        is SweepState.Running -> RunningBody(s)
                        is SweepState.Finished -> ResultBody(s, onLog)
                    }
                }
            }
        }
    }
}

@Composable
internal fun OrbLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Odometer-style count up from 0 to [bytes]. */
@Composable
internal fun CountUpBytes(bytes: Long) {
    val ctx = LocalContext.current
    val anim = remember(bytes) { Animatable(0f) }
    LaunchedEffect(bytes) { anim.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
    RollingText(
        Formatter.formatShortFileSize(ctx, (bytes * anim.value).toLong()),
        style = MaterialTheme.typography.headlineLarge,
    )
}

@Composable
internal fun ReadyBody(count: Int, running: Int, total: Int, cache: Long?, mode: SweepMode, onMode: (SweepMode) -> Unit) {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme
    HeroStatStrip(
        listOf(
            HeroStat(Icons.Rounded.Apps, "Selected", "$count of $total"),
            if (mode == SweepMode.Running) HeroStat(Icons.Rounded.StopCircle, "Running", "$running")
            else HeroStat(Icons.Rounded.Storage, "Cache", cache?.let { Formatter.formatShortFileSize(ctx, it) } ?: "—"),
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
                icon = {},
            ) { Text(m.label, maxLines = 1) }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        mode.blurb + if (mode == SweepMode.Running && !AppRepo.hasUsageAccess(ctx)) " (allow usage access for best results)" else "",
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun RunningBody(s: SweepState.Running) {
    val colors = MaterialTheme.colorScheme
    Text("Now sweeping", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
    Text(
        s.current,
        style = MaterialTheme.typography.headlineSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerLowest.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            if (s.log.isEmpty()) {
                Text(
                    "Results appear here as each app is done.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            s.log.forEachIndexed { i, o ->
                key(o.target.packageName) { Entrance(i) { TickerRow(o) } }
            }
        }
    }
}

@Composable
internal fun TickerRow(o: AppOutcome) {
    val colors = MaterialTheme.colorScheme
    val did = o.stop == StepResult.Done || o.cache == StepResult.Done
    val failed = o.stop == StepResult.NotFound || o.cache == StepResult.NotFound
    val (icon, tint) = when {
        failed -> Icons.Rounded.ErrorOutline to colors.error
        did -> Icons.Rounded.CheckCircle to colors.primary
        else -> Icons.Rounded.RemoveCircleOutline to colors.onSurfaceVariant
    }
    val what = listOfNotNull(
        "Stopped".takeIf { o.stop == StepResult.Done },
        "Cache cleared".takeIf { o.cache == StepResult.Done },
        "Protected".takeIf { o.target.protect && o.stop == StepResult.Skipped && !did },
    ).joinToString(" · ").ifEmpty { if (failed) "Couldn't finish" else "Already clean" }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(o.target.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(what, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
internal fun ResultBody(s: SweepState.Finished, onLog: () -> Unit) {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val skipped = s.outcomes.count { it.stop != StepResult.Done && it.cache != StepResult.Done && it !in s.problems }
    Text(
        if (s.cancelled) "Sweep stopped" else "All clean ✨",
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        "${s.outcomes.size} apps in ${formatElapsed(s.elapsedMs)}" + if (skipped > 0) " · $skipped already clean" else "",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
    )
    Spacer(Modifier.height(14.dp))
    HeroStatStrip(
        buildList {
            if (s.mode.forceStop) add(HeroStat(Icons.Rounded.StopCircle, "Stopped", "${s.stopped}"))
            if (s.mode.clearCache) add(HeroStat(Icons.Rounded.CleaningServices, "Cleared", "${s.cleared}"))
            if (s.mode.clearCache) add(
                HeroStat(Icons.Rounded.Storage, "Freed", s.freedBytes?.let { Formatter.formatShortFileSize(ctx, it) } ?: "—")
            )
        },
        container = colors.surfaceContainerLowest.copy(alpha = 0.6f),
    )
    Spacer(Modifier.height(6.dp))
    TextButton(onClick = onLog) {
        Icon(Icons.Rounded.ListAlt, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Sweep log")
    }
    if (s.problems.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
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

/** Per-app timings and what happened, with a copy button so it can be shared for troubleshooting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SweepLogSheet(s: SweepState.Finished, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    val text = remember(s) {
        buildString {
            appendLine("Katharia log · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
            appendLine("Mode ${s.mode.label} · ${s.outcomes.size} apps · ${formatElapsed(s.elapsedMs)}")
            s.outcomes.forEach { o ->
                appendLine("${o.target.label}: ${o.ms}ms · stop=${o.stop} cache=${o.cache} · ${o.note}")
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerLow.copy(alpha = 1f),
        contentColor = colors.onSurface,
    ) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Sweep log", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = {
                    clipboard.setText(AnnotatedString(text))
                    android.widget.Toast.makeText(ctx, "Log copied", android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
            }
            Text(
                "${formatElapsed(s.elapsedMs)} total · slowest first",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            LazyColumn(Modifier.fillMaxWidth()) {
                itemsIndexed(s.outcomes.sortedByDescending { it.ms }) { _, o ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(o.target.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(o.note.ifEmpty { "—" }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (o.ms < 1000) "${o.ms} ms" else String.format(java.util.Locale.US, "%.1f s", o.ms / 1000f),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (o.ms > 3000) colors.error else colors.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** Bataria's OLED option: true black backgrounds in dark mode. */
@Composable
fun PureBlackRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.material3.ListItem(
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
        leadingContent = { IconBadge(Icons.Rounded.Contrast, MaterialTheme.colorScheme.primary, 36.dp) },
        headlineContent = { Text("Pure black") },
        supportingContent = { Text("True black backgrounds in dark mode, ideal for OLED screens") },
        trailingContent = { androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange) },
    )
}

internal fun formatElapsed(ms: Long): String {
    val sec = (ms / 1000).coerceAtLeast(1)
    return if (sec < 60) "${sec}s" else "${sec / 60}m ${sec % 60}s"
}

@Composable
internal fun LifetimeStrip(l: Lifetime) {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pill("${l.sweeps} ${if (l.sweeps == 1) "sweep" else "sweeps"}", Icons.Rounded.AutoAwesome, colors.primaryContainer, colors.onPrimaryContainer)
        if (l.freedBytes > 0) Pill(
            "${Formatter.formatShortFileSize(ctx, l.freedBytes)} freed all-time", Icons.Rounded.Storage,
            colors.tertiaryContainer, colors.onTertiaryContainer,
        )
        if (l.stopped > 0) Pill("${l.stopped} stops", Icons.Rounded.StopCircle, colors.secondaryContainer, colors.onSecondaryContainer)
    }
}

@Composable
internal fun TileTip() {
    val colors = MaterialTheme.colorScheme
    SectionCard(container = colors.surfaceContainerLow) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.TouchApp, colors.primary, size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                "Tip: add the Sweep tile to Quick Settings, or long-press the Katharia icon → Sweep now, to sweep without opening the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------- setup cards

@Composable
internal fun SetupCard(
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
internal fun ServiceCard(ctx: Context) {
    val restricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    SetupCard(
        Icons.Rounded.Shield,
        "Turn on Katharia",
        "Android only lets apps force-stop and clear cache for other apps by tapping the buttons in Settings. " +
            "Switch on Katharia under Accessibility → Installed apps so it can do that for you." +
            if (restricted) "\n\nSwitch greyed out? Tap App info → ⋮ menu → Allow restricted settings, then try again." else "",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { ctx.openSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Open Accessibility") }
            if (restricted) OutlinedButton(onClick = {
                ctx.openSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
            }) { Text("App info") }
        }
    }
}

@Composable
internal fun UsageCard(ctx: Context) {
    SetupCard(
        Icons.Rounded.Storage,
        "See cache sizes",
        "Optional. Allow “Usage access” to see how much cache each app is holding and how much a sweep frees.",
    ) {
        FilledTonalButton(onClick = { ctx.openSafely(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Allow usage access") }
    }
}

// ---------------------------------------------------------------- list

@Composable
internal fun SearchField(query: String, onChange: (String) -> Unit) {
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
internal fun AppRow(
    app: InstalledApp,
    checked: Boolean,
    first: Boolean,
    last: Boolean,
    showActive: Boolean = false,
    maxCache: Long = 0,
    onToggle: () -> Unit,
) {
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
                if (showActive && app.active && !app.protected) { if (isNotEmpty()) append(" · "); append("Running") }
            }
            if (sub.isNotEmpty() || app.protected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (app.protected) {
                        Icon(Icons.Rounded.Shield, null, tint = colors.primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Protected", style = MaterialTheme.typography.labelMedium, color = colors.primary)
                        if (sub.isNotEmpty()) Text(" · ", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            val cache = app.cacheBytes ?: 0L
            if (maxCache > 0 && cache > 0) {
                val frac by androidx.compose.animation.core.animateFloatAsState(
                    (cache.toFloat() / maxCache).coerceIn(0.02f, 1f), tween(600), label = "cacheBar",
                )
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.9f).height(4.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxWidth(frac).height(4.dp).clip(CircleShape).background(if (checked) colors.primary else colors.outline))
                }
            }
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
    Spacer(Modifier.height(if (last) 0.dp else 2.dp))
}

@Composable
internal fun AppIcon(ctx: Context, app: InstalledApp) {
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
internal fun FloatingSweepBar(
    count: Int,
    label: String?,
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
                    if (running) "Stop sweep" else label ?: "Sweep $count ${if (count == 1) "app" else "apps"}$extra",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

internal fun Context.openSafely(i: Intent) {
    runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
