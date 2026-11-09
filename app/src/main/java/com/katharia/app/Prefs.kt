package com.katharia.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SortBy(val label: String) { Cache("Biggest cache"), Name("A–Z"), Selected("Selected first") }

data class Appearance(
    val themeMode: String = "system", // system | light | dark
    val pureBlack: Boolean = false,   // true black in dark mode, for OLED
    val accent: Int = 0,              // 0 = wallpaper
    val background: String = "aurora",
) {
    fun isDark(systemDark: Boolean) = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
}

/** One finished sweep, for the Insights tab. */
data class SweepRecord(
    val time: Long,
    val mode: SweepMode,
    val apps: Int,
    val stopped: Int,
    val cleared: Int,
    val freedBytes: Long,
    val ms: Long,
)

data class Lifetime(val sweeps: Int, val freedBytes: Long, val stopped: Int, val cleared: Int)

/** Small saved settings: selection, mode, sort, looks and all-time stats. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("katharia", Context.MODE_PRIVATE)

    var excluded: Set<String>
        get() = sp.getStringSet("excluded", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("excluded", v).apply()

    var mode: SweepMode
        get() = runCatching { SweepMode.valueOf(sp.getString("mode", null)!!) }.getOrDefault(SweepMode.Both)
        set(v) = sp.edit().putString("mode", v.name).apply()

    var sort: SortBy
        get() = runCatching { SortBy.valueOf(sp.getString("sort", null)!!) }.getOrDefault(SortBy.Cache)
        set(v) = sp.edit().putString("sort", v.name).apply()

    var lastSeenVersion: Int
        get() = sp.getInt("last_seen_version", 0)
        set(v) = sp.edit().putInt("last_seen_version", v).apply()

    /** Skip caches under 64 KB (they refill instantly). */
    var skipTiny: Boolean
        get() = sp.getBoolean("skip_tiny", true)
        set(v) = sp.edit().putBoolean("skip_tiny", v).apply()

    /** Newest first, up to 60. */
    val history: List<SweepRecord>
        get() = runCatching {
            val arr = org.json.JSONArray(sp.getString("history", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SweepRecord(
                    o.getLong("t"),
                    runCatching { SweepMode.valueOf(o.getString("m")) }.getOrDefault(SweepMode.Both),
                    o.getInt("a"), o.getInt("s"), o.getInt("c"), o.getLong("f"), o.getLong("ms"),
                )
            }
        }.getOrDefault(emptyList())

    /** Apps the user chose to never force-stop. Their cache is still cleared. */
    var keepRunning: Set<String>
        get() = sp.getStringSet("keep_running", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("keep_running", v).apply()

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    var includeSystem: Boolean
        get() = sp.getBoolean("system", false)
        set(v) = sp.edit().putBoolean("system", v).apply()

    var appearance: Appearance
        get() = Appearance(
            themeMode = sp.getString("theme", "system")!!,
            pureBlack = sp.getBoolean("pure_black", false),
            accent = sp.getInt("accent", 0),
            background = sp.getString("bg", "aurora")!!,
        )
        set(v) = sp.edit().putString("theme", v.themeMode).putBoolean("pure_black", v.pureBlack)
            .putInt("accent", v.accent).putString("bg", v.background).apply()

    /** When a sweep last force-stopped apps; "Close running" looks for apps used since then. */
    val lastStopSweepAt: Long get() = sp.getLong("last_stop", 0)

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
        if (s.mode.forceStop && !s.cancelled) sp.edit().putLong("last_stop", System.currentTimeMillis()).apply()
        if (s.outcomes.isNotEmpty()) {
            val rec = org.json.JSONObject()
                .put("t", System.currentTimeMillis()).put("m", s.mode.name).put("a", s.outcomes.size)
                .put("s", s.stopped).put("c", s.cleared).put("f", s.freedBytes ?: 0L).put("ms", s.elapsedMs)
            val old = runCatching { org.json.JSONArray(sp.getString("history", "[]")) }.getOrDefault(org.json.JSONArray())
            val next = org.json.JSONArray().put(rec)
            for (i in 0 until minOf(old.length(), 59)) next.put(old.get(i))
            sp.edit().putString("history", next.toString()).apply()
        }
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
