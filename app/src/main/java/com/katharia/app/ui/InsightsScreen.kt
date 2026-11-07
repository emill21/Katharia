package com.katharia.app.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.katharia.app.SweepRecord

@Composable
fun InsightsScreen(st: KathariaState, padding: PaddingValues) {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val l = st.lifetime
    val history = st.history

    LazyColumn(contentPadding = padding.withPage(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        animatedItem(0) {
            GradientCard(top = colors.tertiaryContainer.copy(alpha = 0.8f), bottom = colors.surfaceContainerLow) {
                Column(Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Storage, colors.tertiary, size = 52.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Freed all-time", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                            RollingText(
                                if (l.freedBytes > 0) Formatter.formatShortFileSize(ctx, l.freedBytes) else "0 B",
                                style = MaterialTheme.typography.displaySmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    HeroStatStrip(
                        listOf(
                            HeroStat(Icons.Rounded.AutoAwesome, "Sweeps", "${l.sweeps}"),
                            HeroStat(Icons.Rounded.StopCircle, "Stopped", "${l.stopped}"),
                            HeroStat(Icons.Rounded.CleaningServices, "Cleared", "${l.cleared}"),
                        ),
                        container = colors.surfaceContainerLowest.copy(alpha = 0.6f),
                    )
                }
            }
        }

        if (history.isEmpty()) {
            animatedItem(1) {
                SectionCard {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Insights, colors.primary, size = 40.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            "Your sweeps will show up here: space freed over time, how fast each one was, and more.",
                            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            val recent = history.take(14).reversed()
            animatedItem(1) { SectionHeader("Space freed per sweep") }
            animatedItem(1) {
                SectionCard {
                    FreedChart(recent, Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 18.dp, vertical = 8.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        Text("Last ${recent.size} sweeps", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(
                            "Best: " + Formatter.formatShortFileSize(ctx, recent.maxOf { it.freedBytes }),
                            style = MaterialTheme.typography.labelMedium, color = colors.primary,
                        )
                    }
                }
            }
            animatedItem(2) {
                val avgMs = history.map { it.ms.toDouble() / it.apps.coerceAtLeast(1) }.average()
                val tiles = listOf(
                    Triple(Icons.Rounded.Speed, "Per app", String.format(java.util.Locale.US, "%.1f s", avgMs / 1000)),
                    Triple(Icons.Rounded.Storage, "Avg freed", Formatter.formatShortFileSize(ctx, history.map { it.freedBytes }.average().toLong())),
                )
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    tiles.forEach { (icon, label, value) -> StatCard(icon, label, value, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
            animatedItem(3) { SectionHeader("Recent sweeps") }
            animatedItem(3) {
                SectionCard {
                    history.take(10).forEach { r ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(modeIcon(r.mode), colors.primary, size = 38.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.mode.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    DateUtils.getRelativeTimeSpanString(r.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() +
                                        " · ${r.apps} apps · ${(r.ms / 1000).coerceAtLeast(1)}s",
                                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                                )
                            }
                            Text(
                                if (r.freedBytes > 0) Formatter.formatShortFileSize(ctx, r.freedBytes) else "${r.stopped} stopped",
                                style = MaterialTheme.typography.labelLarge, color = colors.primary,
                            )
                        }
                    }
                }
            }
        }

        val hogs = st.all.filter { (it.cacheBytes ?: 0) > 0 }.sortedByDescending { it.cacheBytes }
        if (hogs.isNotEmpty()) {
            animatedItem(4) { SectionHeader("Cache right now") }
            animatedItem(4) {
                val total = hogs.sumOf { it.cacheBytes ?: 0 }
                SectionCard {
                    Text(
                        "${Formatter.formatShortFileSize(ctx, total)} across ${hogs.size} apps",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    StackedBar(hogs.take(6).map { it.label to (it.cacheBytes ?: 0) }, total)
                }
            }
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(modifier, shape = CardShape, colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp)) {
            Icon(icon, null, tint = colors.primary)
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
    }
}

/** Rounded bars that grow in, one per sweep. */
@Composable
private fun FreedChart(records: List<SweepRecord>, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val grow = remember(records) { Animatable(0f) }
    LaunchedEffect(records) { grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val max = (records.maxOfOrNull { it.freedBytes } ?: 0L).coerceAtLeast(1L)
    val top = colors.primary
    val bottom = colors.tertiary
    val track = colors.primary.copy(alpha = 0.08f)
    Canvas(modifier) {
        val n = records.size.coerceAtLeast(1)
        val gap = 8.dp.toPx()
        val w = ((size.width - gap * (n - 1)) / n).coerceAtMost(36.dp.toPx())
        val total = w * n + gap * (n - 1)
        var x = (size.width - total) / 2
        records.forEach { r ->
            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 2))
            val h = (size.height * (r.freedBytes.toFloat() / max) * grow.value).coerceAtLeast(w * 0.6f)
            drawRoundRect(
                Brush.verticalGradient(listOf(top, bottom), startY = size.height - h, endY = size.height),
                Offset(x, size.height - h), Size(w, h), CornerRadius(w / 2),
            )
            x += w + gap
        }
    }
}

/** One bar split by app, with a legend. */
@Composable
private fun StackedBar(parts: List<Pair<String, Long>>, total: Long) {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val palette = listOf(colors.primary, colors.tertiary, colors.secondary, colors.primary.copy(alpha = 0.6f), colors.tertiary.copy(alpha = 0.6f), colors.secondary.copy(alpha = 0.6f))
    val rest = colors.outlineVariant
    val grow = remember(parts) { Animatable(0f) }
    LaunchedEffect(parts) { grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Canvas(Modifier.fillMaxWidth().height(18.dp).padding(horizontal = 20.dp)) {
        var x = 0f
        val gap = 3.dp.toPx()
        parts.forEachIndexed { i, (_, b) ->
            val w = size.width * (b.toFloat() / total) * grow.value
            if (w > gap) drawRoundRect(palette[i % palette.size], Offset(x, 0f), Size(w - gap, size.height), CornerRadius(size.height / 2))
            x += w
        }
        if (x < size.width * grow.value) drawRoundRect(rest, Offset(x, 0f), Size(size.width * grow.value - x, size.height), CornerRadius(size.height / 2))
    }
    Spacer(Modifier.height(12.dp))
    parts.forEachIndexed { i, (name, b) ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.width(10.dp).height(10.dp)) { drawCircle(palette[i % palette.size]) }
            Spacer(Modifier.width(10.dp))
            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
            Text(Formatter.formatShortFileSize(ctx, b), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        }
    }
}
