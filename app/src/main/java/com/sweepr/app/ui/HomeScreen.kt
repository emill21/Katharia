package com.sweepr.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sweepr.app.AppRepo
import com.sweepr.app.InstalledApp
import com.sweepr.app.Prefs
import com.sweepr.app.Sweep
import com.sweepr.app.SweepMode
import com.sweepr.app.SweepService
import com.sweepr.app.StepResult
import com.sweepr.app.SweepState
import com.sweepr.app.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }

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
    val selectedCache = selected.sumOf { it.cacheBytes ?: 0L }
    val running = state is SweepState.Running

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Sweepr", fontWeight = FontWeight.SemiBold) },
                scrollBehavior = scroll,
            )
        },
        bottomBar = {
            BottomActions(
                mode = mode,
                onMode = { mode = it; prefs.mode = it },
                count = selected.size,
                cache = if (usageOn && mode.clearCache) selectedCache else null,
                enabled = serviceOn && selected.isNotEmpty() && !running,
                onSweep = { confirm = true },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp,
            ),
        ) {
            when (val s = state) {
                is SweepState.Running -> item { RunningCard(s) }
                is SweepState.Finished -> item { ResultCard(s) { Sweep.dismiss() } }
                SweepState.Idle -> Unit
            }
            if (!serviceOn) item { ServiceCard(ctx) }
            if (!usageOn) item { UsageCard(ctx) }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = includeSystem,
                        onClick = { includeSystem = !includeSystem; prefs.includeSystem = includeSystem },
                        label = { Text("System apps") },
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        excluded = excluded - shown.map { it.packageName }.toSet(); prefs.excluded = excluded
                    }) { Text("All") }
                    TextButton(onClick = {
                        excluded = excluded + shown.map { it.packageName }; prefs.excluded = excluded
                    }) { Text("None") }
                }
            }
            if (includeSystem) item {
                Text(
                    "Force-stopping system apps can briefly drop things like your keyboard, launcher or Bluetooth until they restart.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            if (apps == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            items(shown, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    checked = app.packageName !in excluded,
                    onToggle = {
                        excluded = if (app.packageName in excluded) excluded - app.packageName else excluded + app.packageName
                        prefs.excluded = excluded
                    },
                )
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
                    "Your screen will flip through Settings for each app — about 2–4 seconds each. " +
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

@Composable
private fun BottomActions(
    mode: SweepMode,
    onMode: (SweepMode) -> Unit,
    count: Int,
    cache: Long?,
    enabled: Boolean,
    onSweep: () -> Unit,
) {
    val ctx = LocalContext.current
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(Modifier.navigationBarsPadding().padding(16.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SweepMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = m == mode,
                        onClick = { onMode(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, SweepMode.entries.size),
                    ) { Text(m.label, maxLines = 1) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSweep,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                val extra = if (cache != null && cache > 0) " · ${Formatter.formatShortFileSize(ctx, cache)}" else ""
                Text("Sweep $count apps$extra", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, checked: Boolean, onToggle: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(ctx, app.packageName)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildString {
                app.cacheBytes?.let { append(Formatter.formatShortFileSize(ctx, it)).append(" cache") }
                if (app.system) { if (isNotEmpty()) append(" · "); append("System") }
            }
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun AppIcon(ctx: Context, pkg: String) {
    val bmp by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            AppRepo.icon(ctx, pkg)?.let { runCatching { it.toBitmap(120, 120).asImageBitmap() }.getOrNull() }
        }
    }
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize()) }
    }
}

@Composable
private fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    content: @Composable () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ServiceCard(ctx: Context) {
    val restricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    InfoCard(
        Icons.Rounded.Shield,
        "Turn on Sweepr",
        "Android only lets apps force-stop and clear cache for other apps by tapping the buttons in Settings. " +
            "Turn on Sweepr under Accessibility → Installed apps so it can do that for you." +
            if (restricted) "\n\nIf the switch is greyed out (\"Restricted setting\"): tap App info below → ⋮ menu → Allow restricted settings, then try again." else "",
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
    InfoCard(
        Icons.Rounded.Storage,
        "See cache sizes (optional)",
        "Allow Usage access to show how much cache each app is holding and how much a sweep frees.",
    ) {
        FilledTonalButton(onClick = { ctx.open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Allow Usage access") }
    }
}

@Composable
private fun RunningCard(s: SweepState.Running) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Sweeping ${s.index + 1} of ${s.total}", style = MaterialTheme.typography.titleMedium)
            Text(s.current, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { (s.index + 1f) / s.total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { SweepService.instance?.stop() }) { Text("Stop") }
        }
    }
}

@Composable
private fun ResultCard(s: SweepState.Finished, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    InfoCard(
        Icons.Rounded.CheckCircle,
        if (s.cancelled) "Sweep stopped" else "Sweep done",
        buildString {
            if (s.mode.forceStop) append("Force-stopped ${s.stopped} apps.\n")
            if (s.mode.clearCache) {
                append("Cleared cache for ${s.cleared} apps")
                s.freedBytes?.let { append(" — freed ${Formatter.formatShortFileSize(ctx, it)}") }
                append(".\n")
            }
            val skipped = s.outcomes.count {
                it.stop != StepResult.Done && it.cache != StepResult.Done && it !in s.problems
            }
            if (skipped > 0) append("$skipped were already stopped or had no cache.\n")
            if (s.problems.isNotEmpty()) {
                append("Couldn't finish ${s.problems.size}: ")
                append(s.problems.take(5).joinToString { it.target.label })
                if (s.problems.size > 5) append("…")
            }
        }.trim(),
    ) {
        TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
}

private fun Context.open(i: Intent) {
    runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
