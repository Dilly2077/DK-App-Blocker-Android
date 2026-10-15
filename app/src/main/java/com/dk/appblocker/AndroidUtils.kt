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
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
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
        .filter { !isRecoveryPackage(context, it.packageName) }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

fun homePackages(context: Context): Set<String> = context.packageManager.queryIntentActivities(
    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY
).map { it.activityInfo.packageName }.toSet()

fun isRecoveryPackage(context: Context, pkg: String): Boolean {
    if (pkg == context.packageName || pkg in setOf("android", "com.android.systemui", "com.android.settings",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller",
            "com.android.packageinstaller", "com.google.android.packageinstaller", "com.android.emergency")) return true
    if (pkg in homePackages(context)) return true
    return listOf(Intent(Settings.ACTION_SETTINGS), Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Intent(Intent.ACTION_DIAL), Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)).any {
        context.packageManager.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == pkg
    }
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
            "Device administrator adds a removal confirmation. You can always remove it in Android Settings; it cannot prevent uninstalling."
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
    val activity = context as? FragmentActivity
    if (activity == null) {
        onError("Biometric verification is unavailable here.")
        return
    }
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
    if (BiometricManager.from(context).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
        onError("Biometrics unavailable. Use your recovery password or Android Settings.")
        return
    }
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                onError("Biometric not recognised")
            }
        })
    prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Verify Strict Mode")
        .setSubtitle("Use your enrolled biometric").setAllowedAuthenticators(authenticators)
        .setNegativeButtonText("Cancel").build())
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
