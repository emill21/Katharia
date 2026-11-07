package com.katharia.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.katharia.app.ui.KathariaApp
import com.katharia.app.ui.OnboardingScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.katharia.app.ui.theme.KathariaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        Look.init(this)
        AccessKeeper.ensureEnabled(this)
        val prefs = Prefs(this)
        setContent {
            val look by Look.state.collectAsStateWithLifecycle()
            val dark = look.isDark(isSystemInDarkTheme())
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            var onboarded by remember { mutableStateOf(prefs.onboarded) }
            KathariaTheme(dark, look.accent, look.background, look.pureBlack) {
                AnimatedContent(onboarded, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(250)) }, label = "root") { done ->
                    if (done) KathariaApp(onReplayTour = { onboarded = false })
                    else OnboardingScreen(look) { prefs.onboarded = true; onboarded = true }
                }
            }
        }
        if (savedInstanceState == null) handleShortcut(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcut(intent)
    }

    /** "Sweep now" launcher shortcut. If the service is off, the app just opens on its setup card. */
    private fun handleShortcut(i: Intent?) {
        if (i?.action == SweepLauncher.ACTION_SWEEP) {
            i.action = null
            SweepLauncher.sweepSaved(fromShade = false)
        }
    }
}
