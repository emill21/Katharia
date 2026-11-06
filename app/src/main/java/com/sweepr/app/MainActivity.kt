package com.sweepr.app

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sweepr.app.ui.HomeScreen
import com.sweepr.app.ui.theme.SweeprTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            SweeprTheme(dark) { HomeScreen() }
        }
    }
}

/** Small saved settings: which apps are left out, the chosen mode, and whether system apps show. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("sweepr", Context.MODE_PRIVATE)

    var excluded: Set<String>
        get() = sp.getStringSet("excluded", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("excluded", v).apply()

    var mode: SweepMode
        get() = runCatching { SweepMode.valueOf(sp.getString("mode", null)!!) }.getOrDefault(SweepMode.Both)
        set(v) = sp.edit().putString("mode", v.name).apply()

    var includeSystem: Boolean
        get() = sp.getBoolean("system", false)
        set(v) = sp.edit().putBoolean("system", v).apply()
}
