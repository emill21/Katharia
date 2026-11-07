package com.katharia.app.ui

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.katharia.app.InstalledApp
import com.katharia.app.SweepMode
import com.katharia.app.SweepService
import com.katharia.app.SweepState

@Composable
fun SweepScreen(
    st: KathariaState,
    state: SweepState,
    padding: PaddingValues,
    onOpenApps: () -> Unit,
    onOpenInsights: () -> Unit,
    onLog: (SweepState.Finished) -> Unit,
) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val selected = st.selected

    LazyColumn(contentPadding = padding.withPage(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        animatedItem(0) {
            Hero(
                state = state,
                count = selected.size,
                runningCount = st.runningCount,
                total = st.all.size,
                cache = st.selectedCache,
                mode = st.mode,
                burst = st.burst,
                onDismiss = { com.katharia.app.Sweep.dismiss() },
                onLog = { (state as? SweepState.Finished)?.let(onLog) },
            ) {
                HeroStatStrip(
                    listOf(
                        HeroStat(Icons.Rounded.Apps, "Selected", "${selected.size} of ${st.all.size}"),
                        if (st.mode == SweepMode.Running) HeroStat(Icons.Rounded.StopCircle, "Running", "${st.runningCount}")
                        else HeroStat(Icons.Rounded.Storage, "Cache", st.selectedCache?.let { Formatter.formatShortFileSize(ctx, it) } ?: "—"),
                        HeroStat(Icons.Rounded.AutoAwesome, "Action", st.mode.label),
                    ),
                    container = colors.surfaceContainerLowest.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(16.dp))
                SweepButton(
                    text = when {
                        st.mode == SweepMode.Running -> "Close ${st.runningCount} running"
                        else -> "Sweep ${selected.size} ${if (selected.size == 1) "app" else "apps"}"
                    },
                    sub = if (st.mode.clearCache) st.selectedCache?.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(ctx, it) } else null,
                    enabled = st.canSweep(),
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    st.confirm = true
                }
                if (!st.serviceOn) {
                    Spacer(Modifier.height(8.dp))
                    Text("Turn on Katharia below to start sweeping", style = MaterialTheme.typography.bodySmall, color = colors.error)
                }
            }
        }
        if (state is SweepState.Running) animatedItem(1) {
            Button(
                onClick = { SweepService.instance?.stop() },
                colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Rounded.StopCircle, null)
                Spacer(Modifier.width(8.dp))
                Text("Stop sweep", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (!st.serviceOn) animatedItem(1) { ServiceCard(ctx) }

        if (state !is SweepState.Running) {
            animatedItem(2) { SectionHeader("Action") }
            animatedItem(2) { ModeGrid(st) { if (it != st.mode) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); st.setMode(it) } }
        }

        if (!st.usageOn) animatedItem(3) { UsageCard(ctx) }

        val hogs = st.all.filter { (it.cacheBytes ?: 0) > 0 }.sortedByDescending { it.cacheBytes }.take(5)
        if (hogs.isNotEmpty() && state !is SweepState.Running) {
            animatedItem(4) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Biggest caches", Modifier.weight(1f))
                    TextButton(onClick = onOpenApps) {
                        Text("All apps")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp))
                    }
                }
            }
            animatedItem(4) { HogsCard(hogs, st) }
        }

        if (st.lifetime.sweeps > 0 && state !is SweepState.Running) animatedItem(5) {
            Box(Modifier.clickable(onClick = onOpenInsights)) { LifetimeStrip(st.lifetime) }
        }
        if (st.serviceOn && st.lifetime.sweeps == 0) animatedItem(6) { TileTip() }
    }
}

/** Big gradient pill: the main thing to tap. */
@Composable
private fun SweepButton(text: String, sub: String?, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, tween(250), label = "btnAlpha")
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .pressScale(interaction, 0.96f)
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(colors.primary.copy(alpha = alpha), colors.tertiary.copy(alpha = alpha))))
            .clickable(enabled = enabled, interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = colors.onPrimary)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.titleLarge, color = colors.onPrimary)
            if (sub != null) {
                Spacer(Modifier.width(10.dp))
                Text(
                    sub,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                    modifier = Modifier.clip(CircleShape).background(colors.onPrimary).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** The four actions as big, readable cards instead of a cramped segmented row. */
@Composable
private fun ModeGrid(st: KathariaState, onPick: (SweepMode) -> Unit) {
    val ctx = LocalContext.current
    fun metric(m: SweepMode): String = when (m) {
        SweepMode.Both, SweepMode.CacheOnly -> st.selectedCache?.let { Formatter.formatShortFileSize(ctx, it) + " cache" } ?: "${st.selected.size} apps"
        SweepMode.StopOnly -> "${st.selected.count { !it.protected }} apps"
        SweepMode.Running -> "${st.runningCount} running"
    }
    val modes = SweepMode.entries
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        modes.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { m ->
                    ModeCard(
                        mode = m,
                        icon = modeIcon(m),
                        metric = metric(m),
                        selected = m == st.mode,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) { onPick(m) }
                }
            }
        }
    }
}

internal fun modeIcon(m: SweepMode): ImageVector = when (m) {
    SweepMode.Both -> Icons.Rounded.AutoAwesome
    SweepMode.CacheOnly -> Icons.Rounded.CleaningServices
    SweepMode.StopOnly -> Icons.Rounded.StopCircle
    SweepMode.Running -> Icons.Rounded.Bolt
}

@Composable
private fun ModeCard(mode: SweepMode, icon: ImageVector, metric: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) colors.primaryContainer else colors.surfaceContainerLow, tween(220), label = "modeBg")
    val border by animateColorAsState(if (selected) colors.primary else colors.outlineVariant.copy(alpha = 0.4f), tween(220), label = "modeBorder")
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressScale(interaction, 0.96f)
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, border), RoundedCornerShape(24.dp))
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, colors.primary, size = 40.dp)
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(selected) { Icon(Icons.Rounded.CheckCircle, null, tint = colors.primary) }
        }
        Spacer(Modifier.height(12.dp))
        Text(mode.title, style = MaterialTheme.typography.titleMedium, color = if (selected) colors.onPrimaryContainer else colors.onSurface)
        Spacer(Modifier.height(2.dp))
        Text(
            mode.blurb, style = MaterialTheme.typography.bodySmall,
            color = if (selected) colors.onPrimaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(10.dp))
        Text(metric, style = MaterialTheme.typography.labelLarge, color = colors.primary)
    }
}

/** Top cache holders with bars, tap to include/exclude. */
@Composable
private fun HogsCard(hogs: List<InstalledApp>, st: KathariaState) {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val max = hogs.first().cacheBytes ?: 1L
    SectionCard {
        hogs.forEach { app ->
            val on = st.isSelected(app.packageName)
            Row(
                Modifier.fillMaxWidth().clickable { st.toggle(app.packageName) }.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(ctx, app)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(
                            Formatter.formatShortFileSize(ctx, app.cacheBytes ?: 0),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (on) colors.primary else colors.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    val frac by animateFloatAsState(((app.cacheBytes ?: 0).toFloat() / max).coerceIn(0.03f, 1f), tween(700), label = "hog")
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.12f))) {
                        Box(
                            Modifier.fillMaxWidth(frac).height(6.dp).clip(CircleShape).background(
                                if (on) Brush.horizontalGradient(listOf(colors.primary, colors.tertiary))
                                else Brush.horizontalGradient(listOf(colors.outline, colors.outline))
                            )
                        )
                    }
                }
            }
        }
        Text(
            "Tap an app to include or leave it out.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
        )
    }
}
