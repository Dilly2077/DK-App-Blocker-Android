package com.dk.appblocker

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun isAccessibilityEnabled(context: Context): Boolean {
    val expected = ComponentName(context, BlockAccessibilityService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}

fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

fun installedLaunchableApps(context: Context): List<InstalledApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val pm = context.packageManager
    return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .map {
            InstalledApp(
                label = it.loadLabel(pm)?.toString() ?: it.activityInfo.packageName,
                packageName = it.activityInfo.packageName
            )
        }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

fun startOfTodayMillis(): Long {
    return LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

fun formatDuration(millis: Long): String {
    val minutes = (millis / 60_000L).coerceAtLeast(0)
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

fun epochDay(timestamp: Long): LocalDate =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
