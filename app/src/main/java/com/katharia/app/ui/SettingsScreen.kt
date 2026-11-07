package com.katharia.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.katharia.app.Appearance
import com.katharia.app.BuildConfig
import com.katharia.app.Look

@Composable
fun SettingsScreen(
    st: KathariaState,
    look: Appearance,
    padding: PaddingValues,
    onReplayTour: () -> Unit,
    onChangelog: () -> Unit,
) {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme
    fun update(a: Appearance) = Look.update(ctx, a)

    LazyColumn(contentPadding = padding.withPage(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        animatedItem(0) { SectionHeader("Appearance") }
        animatedItem(0) {
            SectionCard {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Label("Theme")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val modes = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
                        modes.forEachIndexed { i, (k, label) ->
                            SegmentedButton(
                                selected = look.themeMode == k,
                                onClick = { update(look.copy(themeMode = k)) },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                            ) { Text(label) }
                        }
                    }
                }
                PureBlackRow(look.pureBlack) { update(look.copy(pureBlack = it)) }
                Column(Modifier.padding(horizontal = 16.dp)) { Label("Accent color") }
                AccentPicker(selected = look.accent) { update(look.copy(accent = it)) }
                Column(Modifier.padding(horizontal = 16.dp)) { Label("Animated background") }
                BackgroundPicker(selected = look.background) { update(look.copy(background = it)) }
            }
        }

        animatedItem(1) { SectionHeader("Sweeping") }
        animatedItem(1) {
            SectionCard {
                SwitchRow(
                    Icons.Rounded.Speed, "Skip tiny caches",
                    "Caches under 64 KB refill instantly. Skipping them makes sweeps faster.",
                    st.skipTiny,
                ) { st.setSkipTiny(it) }
                SwitchRow(
                    Icons.Rounded.Apps, "Show system apps",
                    "Include Android's built-in apps in the list.",
                    st.includeSystem,
                ) { st.setSystem(it) }
                LinkRow(Icons.Rounded.Shield, "Always protected", "Keyboard, launcher, phone, SMS and other accessibility apps are never force-stopped.") {}
            }
        }

        animatedItem(2) { SectionHeader("Access") }
        animatedItem(2) {
            SectionCard {
                StatusRow(
                    Icons.Rounded.Shield, "Accessibility", "Lets Katharia tap the buttons in Settings for you",
                    on = st.serviceOn, action = "Open",
                ) { ctx.openSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                StatusRow(
                    Icons.Rounded.Storage, "Usage access", "Cache sizes, skipping clean apps, Running mode",
                    on = st.usageOn, action = "Allow",
                ) { ctx.openSafely(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            }
        }
        animatedItem(2) { NeverAskCard(st.selfHeal) }

        animatedItem(3) { SectionHeader("About") }
        animatedItem(3) {
            SectionCard {
                InfoRow("Version", "Katharia ${BuildConfig.VERSION_NAME}", "καθαριά · Greek for “clean”", icon = Icons.Rounded.Info)
                LinkRow(Icons.AutoMirrored.Rounded.ListAlt, "Changelog", "Everything that's new, version by version", onClick = onChangelog)
                LinkRow(Icons.Rounded.AutoAwesome, "Replay welcome tour", "Walk through setup again", onClick = onReplayTour)
                LinkRow(Icons.AutoMirrored.Rounded.OpenInNew, "Source on GitHub", "emill21/Sweepr") {
                    ctx.openSafely(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/emill21/Sweepr")))
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
        leadingContent = { IconBadge(icon, MaterialTheme.colorScheme.primary, 36.dp) },
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { IconBadge(icon, MaterialTheme.colorScheme.primary, 36.dp) },
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
    )
}

@Composable
private fun StatusRow(icon: ImageVector, title: String, sub: String, on: Boolean, action: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { IconBadge(icon, if (on) colors.primary else colors.error, 36.dp) },
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
        trailingContent = {
            if (on) Pill("On", null, colors.primaryContainer, colors.onPrimaryContainer)
            else Box { FilledTonalButton(onClick = onClick, shape = CircleShape) { Text(action) } }
        },
    )
}
