package com.sweepr.app

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
import com.sweepr.app.ui.HomeScreen
import com.sweepr.app.ui.theme.SweeprTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        Look.init(this)
        setContent {
            val look by Look.state.collectAsStateWithLifecycle()
            val dark = look.isDark(isSystemInDarkTheme())
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            SweeprTheme(dark, look.accent, look.background) { HomeScreen() }
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
