package com.dk.appblocker

import android.app.Activity
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
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

fun deviceAdminComponent(context: Context): ComponentName =
    ComponentName(context, StrictDeviceAdminReceiver::class.java)

fun isDeviceAdminActive(context: Context): Boolean {
    val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    return manager.isAdminActive(deviceAdminComponent(context))
}

fun requestDeviceAdmin(context: Context) {
    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent(context))
        putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Strict Mode uses Android device administrator status to make uninstalling the blocker harder while protection is active."
        )
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

fun removeDeviceAdmin(context: Context) {
    val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    if (manager.isAdminActive(deviceAdminComponent(context))) {
        manager.removeActiveAdmin(deviceAdminComponent(context))
    }
}

fun launchBiometricVerification(
    context: Context,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        onError("Biometric verification requires Android 9 or newer.")
        return
    }
    val activity = context as? Activity
    if (activity == null) {
        onError("Biometric verification is unavailable here.")
        return
    }
    val executor = ContextCompat.getMainExecutor(context)
    val cancellationSignal = CancellationSignal()
    val prompt = android.hardware.biometrics.BiometricPrompt.Builder(activity)
        .setTitle("Verify to disable Strict Mode")
        .setSubtitle("Use your fingerprint or face")
        .setNegativeButton("Cancel", executor) { _, _ -> onError("Verification cancelled") }
        .build()
    prompt.authenticate(
        cancellationSignal,
        executor,
        object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                onError(errString?.toString() ?: "Biometric verification failed")
            }

            override fun onAuthenticationFailed() {
                onError("Biometric not recognised")
            }
        }
    )
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
