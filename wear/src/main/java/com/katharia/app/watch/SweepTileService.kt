package com.katharia.app.watch

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.katharia.app.AppRepo
import com.katharia.app.MainActivity
import com.katharia.app.Prefs

/** Tile: one big Sweep button for your saved selection, and how the last sweep went. */
class SweepTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val prefs = Prefs(this)
        val on = AppRepo.isServiceEnabled(this)
        val last = prefs.history.firstOrNull()
        val summary = when {
            !on -> "Tap to finish setup"
            last == null -> prefs.mode.title
            else -> "Last: ${last.stopped} stopped · ${last.cleared} clean"
        }

        val launch = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .addKeyToExtraMapping(MainActivity.EXTRA_SWEEP, ActionBuilders.AndroidBooleanExtra.Builder().setValue(on).build())
                    .build()
            ).build()

        val button = LayoutElementBuilders.Box.Builder()
            .setWidth(dp(96f)).setHeight(dp(96f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(ModifiersBuilders.Clickable.Builder().setId("sweep").setOnClick(launch).build())
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(CONTAINER))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(48f)).build())
                            .build()
                    )
                    .setBorder(ModifiersBuilders.Border.Builder().setWidth(dp(3f)).setColor(argb(PRIMARY)).build())
                    .build()
            )
            .addContent(text("Sweep", 20f, 0xFFFFFFFF.toInt(), bold = true))
            .build()

        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text("Katharia", 13f, MUTED, bold = false))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build())
            .addContent(button)
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build())
            .addContent(text(summary, 12f, PRIMARY, bold = false))
            .build()

        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(expand()).setHeight(expand())
            .addContent(column)
            .build()

        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RES_VERSION)
                .setFreshnessIntervalMillis(30 * 60_000L)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
                .build()
        )
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build())

    private fun text(value: String, size: Float, color: Int, bold: Boolean) =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setMaxLines(2)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(size))
                    .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                    .setColor(argb(color))
                    .build()
            ).build()

    companion object {
        private const val RES_VERSION = "1"
        const val PRIMARY = 0xFF80D5CF.toInt()
        const val CONTAINER = 0xFF136B6E.toInt()
        const val MUTED = 0xFFBEC9C7.toInt()
    }
}
