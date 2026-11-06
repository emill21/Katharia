package com.katharia.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Optional "never ask again" mode. If WRITE_SECURE_SETTINGS has been granted once from a computer
 * (adb shell pm grant com.katharia.app android.permission.WRITE_SECURE_SETTINGS), Katharia can switch
 * its own Accessibility service back on whenever Android turns it off — no trip to Settings.
 */
object AccessKeeper {
    const val ADB_COMMAND = "adb shell pm grant com.katharia.app android.permission.WRITE_SECURE_SETTINGS"

    fun canSelfEnable(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Turns the service on if it's off and we're allowed to. Returns true if it's on afterwards. */
    fun ensureEnabled(context: Context): Boolean {
        if (AppRepo.isServiceEnabled(context)) return true
        if (!canSelfEnable(context)) return false
        return runCatching {
            val cr = context.contentResolver
            val me = ComponentName(context, SweepService::class.java).flattenToString()
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val list = current.split(':').filter { it.isNotBlank() && it != me } + me
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list.joinToString(":"))
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        }.getOrDefault(false)
    }
}
