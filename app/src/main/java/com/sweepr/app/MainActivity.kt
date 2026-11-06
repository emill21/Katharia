package com.sweepr.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sweepr.app.ui.HomeScreen
import com.sweepr.app.ui.SweeprTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SweeprTheme { HomeScreen() }
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
