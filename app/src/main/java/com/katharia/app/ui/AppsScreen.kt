package com.katharia.app.ui

import android.text.format.Formatter
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.katharia.app.InstalledApp
import com.katharia.app.SortBy
import com.katharia.app.SweepMode

private enum class AppFilter(val label: String) { All("All"), Selected("Selected"), Running("Running"), Protected("Protected") }

@Composable
fun AppsScreen(st: KathariaState, padding: PaddingValues) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AppFilter.All) }

    val all = st.all
    val filtered = all.filter { a ->
        (query.isBlank() || a.label.contains(query, true) || a.packageName.contains(query, true)) &&
            when (filter) {
                AppFilter.All -> true
                AppFilter.Selected -> st.isSelected(a.packageName)
                AppFilter.Running -> a.active && !a.protected
                AppFilter.Protected -> a.protected
            }
    }
    val shown = when (st.sort) {
        SortBy.Cache -> filtered.sortedWith(compareByDescending<InstalledApp> { it.cacheBytes ?: 0 }.thenBy { it.label.lowercase() })
        SortBy.Name -> filtered.sortedBy { it.label.lowercase() }
        SortBy.Selected -> filtered.sortedWith(compareBy<InstalledApp> { !st.isSelected(it.packageName) }.thenByDescending { it.cacheBytes ?: 0 })
    }
    val maxCache = st.maxCache

    LazyColumn(contentPadding = padding.withPage(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        animatedItem(0) {
            val colorsTop = colors.secondaryContainer.copy(alpha = 0.8f)
            GradientCard(top = colorsTop, bottom = colors.surfaceContainerLow) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.CheckCircle, colors.primary, size = 48.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            BigNumber("${st.selected.size}", "of ${all.size} selected")
                            st.selectedCache?.let {
                                Text("${Formatter.formatShortFileSize(ctx, it)} of cache in your selection", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { st.selectAll(shown) }) { Text(if (filter == AppFilter.All && query.isBlank()) "Select all" else "Select shown") }
                        OutlinedButton(onClick = { st.selectNone(shown) }) { Text("None") }
                    }
                }
            }
        }
        animatedItem(1) { SearchField(query) { query = it } }
        animatedItem(2) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppFilter.entries.forEach { f ->
                    val count = when (f) {
                        AppFilter.All -> all.size
                        AppFilter.Selected -> st.selected.size
                        AppFilter.Running -> all.count { it.active && !it.protected }
                        AppFilter.Protected -> all.count { it.protected }
                    }
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                        label = { Text("${f.label} · $count") },
                        leadingIcon = when (f) {
                            AppFilter.Running -> ({ Icon(Icons.Rounded.Bolt, null, Modifier.size(18.dp)) })
                            AppFilter.Protected -> ({ Icon(Icons.Rounded.Shield, null, Modifier.size(18.dp)) })
                            else -> null
                        },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(containerColor = colors.surfaceContainerLow),
                    )
                }
            }
        }
        animatedItem(2) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = true,
                    onClick = { st.setSort(SortBy.entries[(st.sort.ordinal + 1) % SortBy.entries.size]); haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                    label = { Text(st.sort.label) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp)) },
                    shape = CircleShape,
                )
                FilterChip(
                    selected = st.includeSystem,
                    onClick = { st.setSystem(!st.includeSystem) },
                    label = { Text("System apps") },
                    leadingIcon = { Icon(Icons.Rounded.Apps, null, Modifier.size(18.dp)) },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(containerColor = colors.surfaceContainerLow),
                )
                if (!st.usageOn) {
                    FilterChip(
                        selected = false,
                        onClick = {},
                        enabled = false,
                        label = { Text("Allow usage access to see sizes") },
                        leadingIcon = { Icon(Icons.Rounded.Storage, null, Modifier.size(18.dp)) },
                        shape = CircleShape,
                    )
                }
            }
        }
        if (st.includeSystem) animatedItem(3) {
            Text(
                "Force-stopping system apps can briefly drop things like Bluetooth until they restart. Protected apps are never force-stopped.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        when {
            st.apps == null -> item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            shown.isEmpty() -> item {
                SectionCard {
                    Text(
                        if (query.isNotBlank()) "No apps match “$query”." else "Nothing here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
        }
        itemsIndexed(shown, key = { _, a -> a.packageName }) { i, app ->
            Box(Modifier.animateItem()) {
                Entrance(minOf(i + 3, 8)) {
                    AppRow(
                        app = app,
                        checked = st.isSelected(app.packageName),
                        first = i == 0,
                        last = i == shown.lastIndex,
                        showActive = st.mode == SweepMode.Running || filter == AppFilter.Running,
                        maxCache = maxCache,
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        st.toggle(app.packageName)
                    }
                }
            }
        }
    }
}
