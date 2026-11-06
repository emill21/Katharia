package com.katharia.app

import android.content.Context

/**
 * The words on the Settings buttons we need to tap. We read them straight out of the
 * phone's own Settings app (so they match its language and skin), then add English
 * fallbacks for Settings builds that renamed their resources.
 */
class SettingsText(context: Context) {
    val forceStop: Set<String>
    val confirm: Set<String>
    val storage: Set<String>
    val clearCache: Set<String>
    val storagePrefix: Set<String>
    /** The "clear" verb on its own ("clear"), for buttons worded slightly differently. */
    val clearWords: Set<String>
    /** The "Cache" row title on an app's storage page. */
    val cacheRow: Set<String>
    val cacheConfirm: Set<String>
    /** Xiaomi's App info has no Storage row: "Clear data" opens a menu with "Clear cache" in it. */
    val miuiClearData: Set<String>

    /** Buttons we must never press, whatever happens. */
    val forbidden: Set<String>

    init {
        val res = runCatching { context.packageManager.getResourcesForApplication(SETTINGS) }.getOrNull()
        fun s(vararg names: String): List<String> = names.mapNotNull { name ->
            res ?: return@mapNotNull null
            val id = res.getIdentifier(name, "string", SETTINGS)
            if (id == 0) null else runCatching { res.getString(id) }.getOrNull()
        }

        forceStop = norm(s("force_stop", "force_stop_btn_text") + listOf("Force stop", "Force Stop"))
        confirm = norm(
            s("dlg_ok", "okay", "force_stop", "force_stop_btn_text") +
                listOf(context.getString(android.R.string.ok), "OK", "Ok", "Force stop", "Stop")
        )
        storage = norm(
            s("storage_settings_for_app", "storage_label", "storage_settings", "storage_use") +
                listOf("Storage & cache", "Storage and cache", "Storage", "Storage usage", "Storage used", "App storage")
        )
        // First word of each storage label ("storage"), for rows like "Storage usage" we don't know by heart.
        storagePrefix = storage.map { it.substringBefore(' ') }.filter { it.length >= 4 }.toSet()
        cacheConfirm = norm(
            s("dlg_ok", "okay", "clear_cache_btn_text") +
                listOf(context.getString(android.R.string.ok), "OK", "Ok", "Clear cache", "Clear", "Delete", "Confirm")
        )
        miuiClearData = norm(listOf("Clear data", "Clear storage"))
        clearCache = norm(s("clear_cache_btn_text") + listOf("Clear cache", "Clear Cache"))
        clearWords = (clearCache.map { it.substringBefore(' ') } + listOf("clear", "delete", "empty")).filter { it.length >= 3 }.toSet()
        cacheRow = norm(s("cache_header_label", "cache_size_text") + listOf("Cache", "Cached data"))
        forbidden = norm(
            s("clear_user_data_text", "clear_storage_btn_text", "uninstall_text", "disable_text") +
                listOf("Clear storage", "Clear data", "Clear all data", "Uninstall", "Disable", "Manage storage")
        )
    }

    companion object {
        const val SETTINGS = "com.android.settings"
        fun norm(list: List<String>) = list.map { it.normalized() }.filter { it.isNotEmpty() }.toSet()
    }
}

/** Lower-case, trimmed, with the odd whitespace and ampersand quirks Settings uses smoothed out. */
fun CharSequence.normalized(): String =
    toString().replace(' ', ' ').replace("&amp;", "&").trim().lowercase()
