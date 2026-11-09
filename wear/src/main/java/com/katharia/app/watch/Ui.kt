package com.katharia.app.watch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/** Katharia's aqua palette (from the icon), tuned for an OLED watch: true black behind everything. */
object Palette {
    val primary = Color(0xFF80D5CF)
    val primaryContainer = Color(0xFF136B6E)
    val onPrimary = Color(0xFF003735)
    val surface = Color(0xFF172120)
    val surfaceHigh = Color(0xFF232B2A)
    val onSurface = Color(0xFFDDE4E2)
    val muted = Color(0xFFBEC9C7)
    val tertiary = Color(0xFFB1CBE8)
    val amber = Color(0xFFF3C46B)
    val red = Color(0xFFFFB4AB)
    val track = Color(0x33A9F0EA)
}

@Composable
fun KathariaWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = Colors(
            primary = Palette.primary,
            primaryVariant = Palette.primaryContainer,
            secondary = Palette.tertiary,
            secondaryVariant = Palette.primaryContainer,
            background = Color.Black,
            surface = Palette.surface,
            error = Palette.red,
            onPrimary = Palette.onPrimary,
            onSecondary = Color(0xFF1A3348),
            onBackground = Palette.onSurface,
            onSurface = Palette.onSurface,
            onSurfaceVariant = Palette.muted,
            onError = Color(0xFF690005),
        ),
        content = content,
    )
}

@Composable
fun StatPill(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier, tint: Color = Palette.primary) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.onSurface, maxLines = 1)
        Text(label, fontSize = 10.sp, color = Palette.muted, maxLines = 1)
    }
}

@Composable
fun PillRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** A label/value line inside a rounded card. */
@Composable
fun InfoCard(title: String, lines: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (title.isNotEmpty()) {
            Text(title, fontSize = 12.sp, color = Palette.primary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
        }
        lines.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(k, fontSize = 12.sp, color = Palette.muted, modifier = Modifier.weight(1f), maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Text(v, fontSize = 12.sp, color = Palette.onSurface, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, maxLines = 2)
            }
        }
    }
}

@Composable
fun PageTitle(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        textAlign = TextAlign.Center,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = Palette.onSurface,
    )
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color = Palette.muted) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        textAlign = TextAlign.Center,
        fontSize = 11.sp,
        color = color,
    )
}

/** Small dots at the bottom showing which page you're on. */
@Composable
fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(if (i == current) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(if (i == current) Palette.onSurface else Palette.muted.copy(alpha = 0.4f))
            )
        }
    }
}

