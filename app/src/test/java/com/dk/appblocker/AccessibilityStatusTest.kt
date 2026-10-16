package com.dk.appblocker

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccessibilityStatusTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val expected get() = ComponentName(context, BlockAccessibilityService::class.java)
    @Before fun reset() {
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, null)
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, null)
        shadowOf(context.getSystemService(android.app.AppOpsManager::class.java)).setMode(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName, android.app.AppOpsManager.MODE_IGNORED)
    }
    @Test fun fullAndShortComponentsMatchButOtherServicesAndMalformedEntriesDoNot() {
        assertTrue(accessibilityListContains(expected.flattenToString(), expected))
        assertTrue(accessibilityListContains(expected.flattenToShortString(), expected))
        assertTrue(accessibilityListContains("bad:${expected.flattenToShortString()}:another/.Service", expected))
        listOf("", "bad", "com.other/.BlockAccessibilityService", "${context.packageName}/.OtherService",
            expected.flattenToString().uppercase()).forEach { assertFalse(accessibilityListContains(it, expected)) }
    }
    @Test fun grantsRevocationsAndRestorationAreReadWithoutRestart() {
        fun set(value: String) = Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, value)
        set(""); assertFalse(isAccessibilityEnabled(context))
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        set(expected.flattenToShortString()); assertTrue(isAccessibilityEnabled(context))
        set(""); assertFalse(isAccessibilityEnabled(context))
        set(expected.flattenToString()); assertTrue(isAccessibilityEnabled(context))
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        assertFalse(isAccessibilityEnabled(context))
    }
    @Test fun managerFallbackRecognisesComponentButCannotOverrideExplicitRevocation() {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val service = AccessibilityServiceInfo()
        ReflectionHelpers.setField(service, "mComponentName", expected)
        shadowOf(manager).setEnabled(true)
        shadowOf(manager).setEnabledAccessibilityServiceList(listOf(service))
        assertTrue(isAccessibilityEnabled(context))
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
        assertFalse(isAccessibilityEnabled(context))
    }
    @Test fun usageAccessIsIndependentFromAccessibility() {
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, expected.flattenToString())
        assertTrue(isAccessibilityEnabled(context)); assertFalse(hasUsageAccess(context))
    }
}
