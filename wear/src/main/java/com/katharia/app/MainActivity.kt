package com.katharia.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.katharia.app.watch.KathariaWearApp
import com.katharia.app.watch.KathariaWearTheme
import com.katharia.app.watch.SetupScreen
import com.katharia.app.watch.WhatsNewScreen
import com.katharia.app.wear.BuildConfig

/** Katharia for Wear OS. The sweep engine (SweepService) is the same one the phone app uses. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AccessKeeper.ensureEnabled(this)
        val prefs = Prefs(this)
        val latest = BuildConfig.VERSION_CODE
        setContent {
            KathariaWearTheme {
                var screen by remember {
                    mutableStateOf(
                        when {
                            !prefs.onboarded -> Screen.Setup
                            prefs.lastSeenVersion < latest && Changelog.load(this).isNotEmpty() -> Screen.WhatsNew
                            else -> Screen.Main
                        }
                    )
                }
                AnimatedContent(screen, transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(200)) }, label = "root") { s ->
                    when (s) {
                        Screen.Setup -> SetupScreen {
                            prefs.onboarded = true
                            prefs.lastSeenVersion = latest
                            screen = Screen.Main
                        }
                        Screen.WhatsNew -> WhatsNewScreen(Changelog.load(this@MainActivity).take(2)) {
                            prefs.lastSeenVersion = latest
                            screen = Screen.Main
                        }
                        Screen.Main -> KathariaWearApp(
                            onReplayTour = { screen = Screen.Setup },
                            onWhatsNew = { screen = Screen.WhatsNew },
                        )
                    }
                }
            }
        }
        if (savedInstanceState == null) handleSweepRequest(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSweepRequest(intent)
    }

    override fun onResume() {
        super.onResume()
        AccessKeeper.ensureEnabled(this)
        runCatching {
            androidx.wear.tiles.TileService.getUpdater(this)
                .requestUpdate(com.katharia.app.watch.SweepTileService::class.java)
        }
    }

    /** From the tile: sweep the saved selection straight away (if the service is on). */
    private fun handleSweepRequest(i: Intent?) {
        if (i == null) return
        if (i.action == SweepLauncher.ACTION_SWEEP || i.getBooleanExtra(EXTRA_SWEEP, false)) {
            i.action = null
            i.removeExtra(EXTRA_SWEEP)
            if (Prefs(this).onboarded) SweepLauncher.sweepSaved(fromShade = false)
        }
    }

    enum class Screen { Setup, WhatsNew, Main }

    companion object {
        const val EXTRA_SWEEP = "sweep"
    }
}
