package dev.minimalist.launcher

import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

object SystemInfo {
    @Suppress("DEPRECATION")
    fun hasUsageAccess(c: Context): Boolean {
        val ops = c.getSystemService(AppOpsManager::class.java)
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
    }

    /** Foreground time since local midnight across all apps except this launcher, in minutes. */
    fun usageTodayMinutes(c: Context): Long {
        val usm = c.getSystemService(UsageStatsManager::class.java)
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val ms = usm.queryAndAggregateUsageStats(start, System.currentTimeMillis())
            .filterKeys { it != c.packageName }.values.sumOf { it.totalTimeInForeground }
        return ms / 60_000
    }

    /** True when pressing Home would open this launcher without asking (it is the chosen home app). */
    fun isDefaultHome(c: Context): Boolean {
        val home = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
        val chosen = c.packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return chosen?.activityInfo?.packageName == c.packageName
    }

    fun hasDndAccess(c: Context) = c.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun isDndOn(c: Context): Boolean {
        val f = c.getSystemService(NotificationManager::class.java).currentInterruptionFilter
        return f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    fun setDnd(c: Context, on: Boolean) = c.getSystemService(NotificationManager::class.java)
        .setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
}
