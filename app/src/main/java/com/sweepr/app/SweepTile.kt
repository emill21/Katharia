package com.sweepr.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: one tap sweeps your saved selection. */
class SweepTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val on = SweepService.instance != null
        tile.state = Tile.STATE_INACTIVE
        tile.label = "Sweep"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val l = Prefs(this).lifetime
            tile.subtitle = when {
                !on -> "Tap to set up"
                l.sweeps > 0 -> "${l.sweeps} sweeps so far"
                else -> Prefs(this).mode.label
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (SweepService.instance == null) {
            openApp()
            return
        }
        qsTile?.apply { state = Tile.STATE_ACTIVE; updateTile() }
        if (!SweepLauncher.sweepSaved(fromShade = true)) openApp()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }

}
