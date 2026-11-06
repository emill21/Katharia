package com.sweepr.app

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
                listOf("Storage & cache", "Storage and cache", "Storage")
        )
        clearCache = norm(s("clear_cache_btn_text") + listOf("Clear cache", "Clear Cache"))
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
