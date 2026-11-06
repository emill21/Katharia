package com.sweepr.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SortBy(val label: String) { Cache("Biggest cache"), Name("A–Z"), Selected("Selected first") }

data class Appearance(
    val themeMode: String = "system", // system | light | dark
    val accent: Int = 0,              // 0 = wallpaper
    val background: String = "aurora",
) {
    fun isDark(systemDark: Boolean) = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
}

data class Lifetime(val sweeps: Int, val freedBytes: Long, val stopped: Int, val cleared: Int)

/** Small saved settings: selection, mode, sort, looks and all-time stats. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("sweepr", Context.MODE_PRIVATE)

    var excluded: Set<String>
        get() = sp.getStringSet("excluded", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("excluded", v).apply()

    var mode: SweepMode
        get() = runCatching { SweepMode.valueOf(sp.getString("mode", null)!!) }.getOrDefault(SweepMode.Both)
        set(v) = sp.edit().putString("mode", v.name).apply()

    var sort: SortBy
        get() = runCatching { SortBy.valueOf(sp.getString("sort", null)!!) }.getOrDefault(SortBy.Cache)
        set(v) = sp.edit().putString("sort", v.name).apply()

    var includeSystem: Boolean
        get() = sp.getBoolean("system", false)
        set(v) = sp.edit().putBoolean("system", v).apply()

    var appearance: Appearance
        get() = Appearance(
            sp.getString("theme", "system")!!,
            sp.getInt("accent", 0),
            sp.getString("bg", "aurora")!!,
        )
        set(v) = sp.edit().putString("theme", v.themeMode).putInt("accent", v.accent).putString("bg", v.background).apply()

    val lifetime: Lifetime
        get() = Lifetime(sp.getInt("sweeps", 0), sp.getLong("freed", 0), sp.getInt("stopped", 0), sp.getInt("cleared", 0))

    fun record(s: SweepState.Finished) {
        val l = lifetime
        sp.edit()
            .putInt("sweeps", l.sweeps + 1)
            .putLong("freed", l.freedBytes + (s.freedBytes ?: 0))
            .putInt("stopped", l.stopped + s.stopped)
            .putInt("cleared", l.cleared + s.cleared)
            .apply()
    }
}

/** App-wide observable appearance, so the theme updates instantly from the picker. */
object Look {
    private val _state = MutableStateFlow(Appearance())
    val state: StateFlow<Appearance> = _state.asStateFlow()
    private var loaded = false

    fun init(context: Context) {
        if (!loaded) { _state.value = Prefs(context).appearance; loaded = true }
    }

    fun update(context: Context, a: Appearance) {
        _state.value = a
        Prefs(context).appearance = a
    }
}
