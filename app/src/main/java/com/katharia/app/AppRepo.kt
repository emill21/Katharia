package com.katharia.app

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.os.Build
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.telecom.TelecomManager
import android.content.pm.ApplicationInfo
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings

data class InstalledApp(
    val packageName: String,
    val label: String,
    val system: Boolean,
    val cacheBytes: Long?,
    /** Keyboard, launcher, phone, SMS or another accessibility app: never force-stopped. */
    val protected: Boolean = false,
    /** Not force-stopped and used recently, so probably still running something. */
    val active: Boolean = false,
)

object AppRepo {

    fun load(context: Context, includeSystem: Boolean): List<InstalledApp> {
        val pm = context.packageManager
        val withCache = hasUsageAccess(context)
        val self = context.packageName
        val guarded = protectedPackages(context)
        val recent = recentlyActive(context, Prefs(context).lastStopSweepAt)
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
                    protected = info.packageName in guarded,
                    active = info.flags and ApplicationInfo.FLAG_STOPPED == 0 && (recent == null || info.packageName in recent),
                )
            }
            .sortedWith(compareByDescending<InstalledApp> { it.cacheBytes ?: 0 }.thenBy { it.label.lowercase() })
            .toList()
    }

    private fun cacheOf(context: Context, info: ApplicationInfo): Long? = runCatching {
        val ssm = context.getSystemService(StorageStatsManager::class.java)
        ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, info.packageName, Process.myUserHandle()).cacheBytes
    }.getOrNull()

    fun cacheOf(context: Context, pkg: String): Long? {
        if (!hasUsageAccess(context)) return null
        return runCatching { cacheOf(context, context.packageManager.getApplicationInfo(pkg, 0)) }.getOrNull()
    }

    /**
     * Apps opened (or running a foreground service) since [since] — capped at the last 24 hours.
     * Null without usage access, meaning "can't tell".
     */
    fun recentlyActive(context: Context, since: Long): Set<String>? {
        if (!hasUsageAccess(context)) return null
        val now = System.currentTimeMillis()
        val from = maxOf(since, now - 24 * 60 * 60 * 1000L)
        return runCatching {
            val usm = context.getSystemService(UsageStatsManager::class.java)
            usm.queryAndAggregateUsageStats(from, now).values.filter { u ->
                u.lastTimeUsed >= from ||
                    (Build.VERSION.SDK_INT >= 29 && u.lastTimeForegroundServiceUsed >= from)
            }.map { it.packageName }.toSet()
        }.getOrNull()
    }

    /** True if the app is already force-stopped (and hasn't been opened since), like Settings checks. */
    fun isStopped(context: Context, pkg: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_STOPPED != 0
    }.getOrDefault(false)

    fun totalCache(context: Context, targets: List<Target>): Long? {
        if (!hasUsageAccess(context)) return null
        val pm = context.packageManager
        return targets.sumOf { t ->
            runCatching { cacheOf(context, pm.getApplicationInfo(t.packageName, 0)) }.getOrNull() ?: 0L
        }
    }

    /** Apps that would break something visible if force-stopped mid-sweep. */
    fun protectedPackages(context: Context): Set<String> {
        val out = mutableSetOf<String>()
        val cr = context.contentResolver
        runCatching {
            Settings.Secure.getString(cr, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.let { ComponentName.unflattenFromString(it)?.packageName }?.let(out::add)
        }
        runCatching {
            Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')?.mapNotNull { ComponentName.unflattenFromString(it)?.packageName }?.let(out::addAll)
        }
        runCatching {
            context.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName?.let(out::add)
        }
        runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let(out::add) }
        runCatching { Telephony.Sms.getDefaultSmsPackage(context)?.let(out::add) }
        out.remove("android")
        return out
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
