package com.sweepr.app

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings

data class InstalledApp(
    val packageName: String,
    val label: String,
    val system: Boolean,
    val cacheBytes: Long?,
)

object AppRepo {

    fun load(context: Context, includeSystem: Boolean): List<InstalledApp> {
        val pm = context.packageManager
        val withCache = hasUsageAccess(context)
        val self = context.packageName
        return pm.getInstalledApplications(0)
            .asSequence()
            .filter { it.packageName != self && it.packageName != SettingsText.SETTINGS }
            .filter { it.enabled }
            .map { info ->
                val system = info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                info to system
            }
            .filter { (_, system) -> includeSystem || !system }
            .map { (info, system) ->
                InstalledApp(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    system = system,
                    cacheBytes = if (withCache) cacheOf(context, info) else null,
                )
            }
            .sortedWith(compareByDescending<InstalledApp> { it.cacheBytes ?: 0 }.thenBy { it.label.lowercase() })
            .toList()
    }

    private fun cacheOf(context: Context, info: ApplicationInfo): Long? = runCatching {
        val ssm = context.getSystemService(StorageStatsManager::class.java)
        ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, info.packageName, Process.myUserHandle()).cacheBytes
    }.getOrNull()

    fun totalCache(context: Context, targets: List<Target>): Long? {
        if (!hasUsageAccess(context)) return null
        val pm = context.packageManager
        return targets.sumOf { t ->
            runCatching { cacheOf(context, pm.getApplicationInfo(t.packageName, 0)) }.getOrNull() ?: 0L
        }
    }

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isServiceEnabled(context: Context): Boolean {
        if (SweepService.instance != null) return true
        val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, SweepService::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun icon(context: Context, pkg: String) =
        runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
}
