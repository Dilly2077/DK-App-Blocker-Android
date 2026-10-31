package com.dk.appblocker

import android.app.UiAutomation
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.*
import org.junit.Assert.*

/** Representative apps only. Passing these tests cannot establish any real bank's security policy. */
class FinancialCompatibilityDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val finance = listOf("com.dk.compatfixture.financeone", "com.dk.compatfixture.financetwo")
    private val social = "com.dk.compatfixture.social"
    private lateinit var device: UiDevice
    private var previousServices: String? = null
    private var previousEnabled = 0
    private var prepared = false
    private fun shell(command: String) = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        .executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText() }
    @Before fun setup() {
        Assume.assumeTrue("Use the isolated local audit app", context.packageName == "com.dk.appblocker.audit")
        Assume.assumeTrue("Install the three locally built compatibility fixtures first", (finance + social).all {
            runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess
        })
        device = UiDevice.getInstance(instrumentation)
        device.wakeUp(); device.pressHome()
        androidx.test.uiautomator.Configurator.getInstance().uiAutomationFlags = UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        previousServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        previousEnabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        context.getSharedPreferences("dk_blocker_prefs", 0).edit().clear().commit()
        val services = listOfNotNull(previousServices?.takeIf { it.isNotBlank() }, ComponentName(context, BlockAccessibilityService::class.java).flattenToString()).joinToString(":")
        shell("settings put secure enabled_accessibility_services $services"); shell("settings put secure accessibility_enabled 1")
        prepared = true
        Thread.sleep(1500)
        Prefs.savePlans(context, listOf(BlockPlan(name = "Social fixture", triggerType = TriggerType.MANUAL, manualActive = true, packages = listOf(social))))
    }
    @After fun cleanup() {
        if (!prepared) return
        context.getSharedPreferences("dk_blocker_prefs", 0).edit().clear().commit()
        device.pressHome()
        if (previousServices == null) shell("settings delete secure enabled_accessibility_services")
        else shell("settings put secure enabled_accessibility_services $previousServices")
        shell("settings put secure accessibility_enabled $previousEnabled")
    }
    private fun open(pkg: String) { shell("am start -W -n $pkg/com.dk.compatfixture.FixtureActivity") }
    @Test fun bothUnselectedFinanceFixturesStayUsableWithBlockingEnabled() {
        finance.forEach {
            open(it)
            assertTrue(device.wait(Until.hasObject(By.text("Private finance fixture")), 5000))
            assertFalse(device.hasObject(By.text("Blocked")))
        }
        assertTrue(Prefs.getBlockEvents(context).none { it.packageName in finance })
    }
    @Test fun rapidSwitchesClearProtectionBeforeShowingAnUnselectedFinanceFixture() {
        repeat(3) {
            open(social); assertTrue(device.wait(Until.hasObject(By.text("Blocked")), 8000))
            device.pressHome(); open(finance[it % finance.size])
            assertTrue(device.wait(Until.hasObject(By.text("Private finance fixture")), 5000))
            assertFalse(device.hasObject(By.text("Blocked")))
        }
        assertTrue(Prefs.getBlockEvents(context).all { it.packageName == social })
    }
}
