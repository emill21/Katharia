package com.katharia.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.katharia.app.AccessKeeper
import com.katharia.app.AppRepo
import com.katharia.app.Appearance
import com.katharia.app.Look
import kotlinx.coroutines.launch

private const val PAGES = 6

/** First-launch tour, in the same style as Bataria's: what it is, how it works, then guided setup. */
@Composable
fun OnboardingScreen(appearance: Appearance, onFinish: () -> Unit) {
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize().background(colors.surface)) {
        AnimatedBackground(appearance.background, Modifier.fillMaxSize())
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Step ${pager.currentPage + 1} of $PAGES",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp).weight(1f),
                    )
                    if (pager.currentPage < PAGES - 1) {
                        TextButton(onClick = { scope.launch { pager.animateScrollToPage(PAGES - 1) } }) { Text("Skip") }
                    }
                }
                HorizontalPager(state = pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                    ) {
                        when (page) {
                            0 -> WelcomePage()
                            1 -> FeaturesPage()
                            2 -> HowPage()
                            3 -> AccessPage()
                            4 -> LookPage(appearance)
                            else -> DonePage()
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(PAGES) { i ->
                            val selected = i == pager.currentPage
                            val w by animateDpAsState(if (selected) 26.dp else 8.dp, label = "dot")
                            val c by animateColorAsState(
                                if (selected) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.3f), label = "dotColor",
                            )
                            Box(Modifier.width(w).height(8.dp).clip(CircleShape).background(c))
                        }
                    }
                    if (pager.currentPage > 0 && pager.currentPage < PAGES - 1) {
                        OutlinedButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) { Text("Back") }
                        Spacer(Modifier.width(8.dp))
                    }
                    val last = pager.currentPage == PAGES - 1
                    Button(onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                        Text(
                            when {
                                last -> "Start"
                                pager.currentPage == 0 -> "Let's go"
                                else -> "Next"
                            }
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- pages

@Composable
private fun PageTitle(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(10.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) { Column(Modifier.padding(vertical = 8.dp), content = content) }
}

@Composable
private fun WelcomePage() {
    val colors = MaterialTheme.colorScheme
    val demo = rememberInfiniteTransition(label = "demo")
    val level by demo.animateFloat(
        initialValue = 0.08f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Reverse),
        label = "demoLevel",
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(4.dp))
        CacheOrb(level = level, spinning = true, modifier = Modifier.fillMaxWidth(0.8f).aspectRatio(1f)) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = colors.onSurface, modifier = Modifier.size(64.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Welcome to", style = MaterialTheme.typography.titleLarge, color = colors.onSurfaceVariant)
        Text(
            "Katharia",
            fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp,
            color = colors.primary,
        )
        Text("καθαριά · Greek for “clean”", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        Text(
            "Clear the cache of every app and force-stop them all in one tap, then watch the space come back.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text("Takes about a minute to set up.", style = MaterialTheme.typography.labelLarge, color = colors.primary)
    }
}

@Composable
private fun FeaturesPage() {
    PageTitle("What you get", "Everything you'd do app by app in Settings, done for you.")
    val features = listOf(
        Triple(Icons.Rounded.CleaningServices, "Clear every cache", "Pick apps once; Katharia taps Clear cache for each."),
        Triple(Icons.Rounded.StopCircle, "Force stop them all", "Close apps running in the background in one go."),
        Triple(Icons.Rounded.Speed, "Fast and smart", "Apps that are already stopped or have no cache are skipped instantly."),
        Triple(Icons.Rounded.Shield, "Auto-protect", "Your keyboard, launcher, phone and SMS apps are never force-stopped."),
        Triple(Icons.Rounded.TouchApp, "One-tap sweeps", "A Quick Settings tile and a home-screen shortcut."),
        Triple(Icons.Rounded.Insights, "See what you freed", "Cache sizes per app and all-time totals."),
    )
    features.forEachIndexed { i, (icon, title, body) ->
        Entrance(i) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, MaterialTheme.colorScheme.primary, size = 48.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun HowPage() {
    PageTitle(
        "How it works",
        "Android doesn't let apps clear other apps' cache directly, so Katharia does it the way you would.",
    )
    GlassCard {
        Step("1", "Opens each app's page", "Katharia jumps to the app's App info screen in Settings.")
        Step("2", "Taps the buttons", "Force stop → OK, then Storage → Clear cache. About a second per app.")
        Step("3", "Comes back with results", "You'll see what was stopped, what was cleared and how much space was freed.")
    }
    Spacer(Modifier.height(16.dp))
    GlassCard {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.VerifiedUser, MaterialTheme.colorScheme.primary, size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                "It never taps Clear storage, Clear data or Uninstall, only acts when you start a sweep, and nothing leaves your phone.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Step(n: String, title: String, body: String) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) { Text(n, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AccessPage() {
    val ctx = LocalContext.current
    var serviceOn by remember { mutableStateOf(AppRepo.isServiceEnabled(ctx)) }
    var usageOn by remember { mutableStateOf(AppRepo.hasUsageAccess(ctx)) }
    var selfHeal by remember { mutableStateOf(AccessKeeper.canSelfEnable(ctx)) }
    LifecycleResumeEffect(Unit) {
        AccessKeeper.ensureEnabled(ctx)
        serviceOn = AppRepo.isServiceEnabled(ctx)
        usageOn = AppRepo.hasUsageAccess(ctx)
        selfHeal = AccessKeeper.canSelfEnable(ctx)
        onPauseOrDispose { }
    }
    val restricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    PageTitle("Turn it on", "Two quick switches. You only do this once; updates keep them on.")
    if (restricted) {
        AccessCard(
            n = "1",
            done = serviceOn,
            title = "Allow restricted settings",
            body = "Android blocks Accessibility for apps installed outside the Play Store until you allow it. " +
                "Tap below → ⋮ menu (top right) → Allow restricted settings.",
            button = "Open App info",
        ) { ctx.open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))) }
        Spacer(Modifier.height(12.dp))
    }
    AccessCard(
        n = if (restricted) "2" else "1",
        done = serviceOn,
        title = "Switch on Katharia",
        body = "In Accessibility, open Installed apps (or Downloaded apps) → Katharia → turn it on.",
        button = "Open Accessibility",
    ) { ctx.open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    Spacer(Modifier.height(12.dp))
    AccessCard(
        n = if (restricted) "3" else "2",
        done = usageOn,
        title = "See cache sizes",
        badge = "Recommended",
        body = "Usage access lets Katharia measure cache, skip apps that are already clean and show what you freed.",
        button = "Allow usage access",
    ) { ctx.open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
    Spacer(Modifier.height(20.dp))
    NeverAskCard(selfHeal)
}

@Composable
private fun AccessCard(
    n: String,
    done: Boolean,
    title: String,
    body: String,
    button: String,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val border by animateColorAsState(if (done) colors.primary else colors.outlineVariant.copy(alpha = 0.5f), label = "accBorder")
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (done) colors.primaryContainer else colors.surfaceContainerLow,
            contentColor = if (done) colors.onPrimaryContainer else colors.onSurface,
        ),
        border = BorderStroke(if (done) 2.dp else 1.dp, border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(if (done) colors.primary else colors.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Rounded.CheckCircle, null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
                else Text(n, color = colors.primary, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (badge != null && !done) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            badge, style = MaterialTheme.typography.labelMedium, color = colors.onPrimary,
                            modifier = Modifier.clip(CircleShape).background(colors.primary).padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(if (done) "Done" else body, style = MaterialTheme.typography.bodyMedium)
                if (!done) {
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = onClick) { Text(button) }
                }
            }
        }
    }
}

/** Optional: a one-time grant from a computer so Katharia can turn its own switch back on. */
@Composable
fun NeverAskCard(granted: Boolean) {
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) colors.primaryContainer else colors.tertiaryContainer,
            contentColor = if (granted) colors.onPrimaryContainer else colors.onTertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.Computer, null)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (granted) "Auto-restore: on" else "Optional: never switch it on again",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (granted) "If Android ever turns Katharia's Accessibility switch off, Katharia turns it straight back on."
                else "If your phone keeps turning Katharia off, you can give it permission to switch itself back on. " +
                    "It takes one command from a computer with ADB, once.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!granted) {
                if (!open) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { open = true }) { Text("Show me how") }
                } else {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "1. Turn on Developer options → USB debugging.\n2. Plug into a computer with ADB and run:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(14.dp), color = colors.surfaceContainerLowest.copy(alpha = 0.7f)) {
                        Text(
                            AccessKeeper.ADB_COMMAND,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurface,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = {
                        clipboard.setText(AnnotatedString(AccessKeeper.ADB_COMMAND))
                        Toast.makeText(ctx, "Command copied", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy command")
                    }
                }
            }
        }
    }
}

@Composable
private fun LookPage(appearance: Appearance) {
    val ctx = LocalContext.current
    PageTitle("Make it yours", "Pick a color and a background. Change them any time with the palette button.")
    GlassCard {
        Column(Modifier.padding(horizontal = 16.dp)) {
            FieldLabel("Theme")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val modes = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
                modes.forEachIndexed { i, (k, label) ->
                    SegmentedButton(
                        selected = appearance.themeMode == k,
                        onClick = { Look.update(ctx, appearance.copy(themeMode = k)) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                    ) { Text(label) }
                }
            }
            FieldLabel("Accent color", top = 18.dp)
        }
        AccentPicker(selected = appearance.accent) { Look.update(ctx, appearance.copy(accent = it)) }
        Column(Modifier.padding(horizontal = 16.dp)) { FieldLabel("Animated background", top = 18.dp) }
        BackgroundPicker(selected = appearance.background) { Look.update(ctx, appearance.copy(background = it)) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun FieldLabel(text: String, top: androidx.compose.ui.unit.Dp = 8.dp) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = top, bottom = 8.dp),
    )
}

@Composable
private fun DonePage() {
    val colors = MaterialTheme.colorScheme
    val pulse = rememberInfiniteTransition(label = "done")
    val scale by pulse.animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "doneScale")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier.size(150.dp).clip(CircleShape).background(colors.primaryContainer)
                .border(3.dp, colors.primary.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.CheckCircle, null, tint = colors.primary, modifier = Modifier.size((84 * scale).dp))
        }
        Spacer(Modifier.height(28.dp))
        Text("You're all set", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "Pick the apps you want swept, choose an action and tap Sweep. Your choices are remembered.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        GlassCard {
            Tip(Icons.Rounded.TouchApp, "Add the Sweep tile: pull down Quick Settings › edit (pencil) › drag in Sweep.")
            Tip(Icons.Rounded.AutoAwesome, "Long-press the Katharia icon › Sweep now, to sweep straight from the home screen.")
            Tip(Icons.Rounded.Storage, "After a sweep, open Sweep log to see how long each app took.")
            Tip(Icons.Rounded.Palette, "Replay this tour any time from the palette button.")
        }
    }
}

@Composable
private fun Tip(icon: ImageVector, text: String) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun Context.open(i: Intent) {
    runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
