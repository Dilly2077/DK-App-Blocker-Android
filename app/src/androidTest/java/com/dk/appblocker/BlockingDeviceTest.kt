package com.dk.appblocker

import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlockingDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val fixturePackage: String get() = instrumentation.context.packageName
    private lateinit var device: UiDevice
    private var previousServices: String? = null
    private var previousEnabled = 0
    private fun shell(command: String): String = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        .executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText() }

    @Before fun setup() {
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        androidx.test.uiautomator.Configurator.getInstance().uiAutomationFlags = UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        device = UiDevice.getInstance(instrumentation)
        device.wakeUp(); device.pressHome()
        context.getSharedPreferences("dk_blocker_prefs", 0).edit().clear().commit()
        previousServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        previousEnabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        val service = "${context.packageName}/${BlockAccessibilityService::class.java.name}"
        val enabled = listOfNotNull(previousServices?.takeIf { it.isNotBlank() }, service).joinToString(":")
        shell("settings put secure enabled_accessibility_services '$enabled'")
        shell("settings put secure accessibility_enabled 1")
        val until = System.currentTimeMillis() + 10_000
        while (!isAccessibilityEnabled(context) && System.currentTimeMillis() < until) Thread.sleep(100)
        assertTrue(isAccessibilityEnabled(context))
        Thread.sleep(1500) // Allow the system's service binding to complete.
    }
    @After fun cleanup() {
        context.getSharedPreferences("dk_blocker_prefs", 0).edit().clear().commit()
        device.pressHome()
        if (previousServices == null) shell("settings delete secure enabled_accessibility_services")
        else shell("settings put secure enabled_accessibility_services '$previousServices'")
        shell("settings put secure accessibility_enabled $previousEnabled")
    }
    private fun configure(breaks: Boolean = false) {
        Prefs.savePlans(context, listOf(BlockPlan(name = "Device test", triggerType = TriggerType.MANUAL,
            packages = listOf(fixturePackage), manualActive = true, allowBreaks = breaks)))
    }
    private fun openFixture(keyboard: Boolean = false) { shell("am start -W -n $fixturePackage/com.dk.appblocker.FixtureActivity --ez keyboard $keyboard") }
    private fun blocked() = device.wait(Until.hasObject(By.text("Blocked")), 8000)

    @Test fun realExternalAppBlocksAndBackAndHomeEscapeWithoutTrapping() {
        configure(); openFixture(); assertTrue("Block screen did not launch", blocked())
        assertTrue(device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "blocking.png")))
        device.pressBack()
        assertTrue(device.wait(Until.gone(By.text("Blocked")), 3000))
        openFixture(); assertTrue(blocked())
        device.findObject(By.text("Go home")).click()
        assertTrue(device.wait(Until.gone(By.text("Blocked")), 3000))
    }
    @Test fun rapidSwitchesBlockAgainWithoutDuplicateHistorySpam() {
        configure()
        repeat(3) { openFixture(); assertTrue(blocked()); device.pressHome() }
        assertEquals(1, Prefs.getBlockEvents(context).count { it.packageName == fixturePackage })
    }
    @Test fun allowedAppAndRecoverySettingsRemainUsableDuringStrictMode() {
        configure()
        Prefs.saveStrict(context, StrictSettings(enabled = true, unlockMethod = "TIMER",
            lockUntil = System.currentTimeMillis() + 300_000, lockElapsedEnd = android.os.SystemClock.elapsedRealtime() + 300_000,
            lockBoot = Prefs.boot(context)))
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings").depth(0)), 5000))
        assertFalse(device.hasObject(By.text("Blocked")))
        shell("cmd statusbar expand-notifications")
        Thread.sleep(500)
        assertFalse(device.hasObject(By.text("Blocked")))
        shell("cmd statusbar collapse")
        device.pressHome(); device.pressRecentApps(); Thread.sleep(500); device.pressHome()
        assertFalse(device.hasObject(By.text("Blocked")))
    }
    @Test fun breaksAndExpiredBreaksAreEnforcedWhileAppStaysOpen() {
        configure(true); openFixture(); assertTrue(blocked())
        device.findObject(By.text("Take a 5-minute break")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Fixture distraction")), 5000))
        val match = run { Prefs.allowPackageUntil(context, fixturePackage, 0); RuleEngine.shouldBlock(context, fixturePackage) }
        Prefs.allowPackageUntil(context, fixturePackage, System.currentTimeMillis() + 1200, match.policyToken)
        assertTrue("Expired break did not reblock without a window switch", blocked())
    }
    @Test fun blockedScreenCustomizationRendersFromSavedPreferences() {
        Prefs.saveBlockScreen(context, BlockScreenSettings(title = "Focus now", message = "Custom {app}", backgroundHex = "#07110C", accentHex = "#FFD166"))
        configure(); openFixture()
        assertTrue(device.wait(Until.hasObject(By.text("Focus now")), 8000))
        assertTrue(device.hasObject(By.textStartsWith("Custom ")))
        assertTrue(device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "custom-blocking.png")))
    }
    @Test fun keyboardDoesNotBypassExpiredBreak() {
        configure(true)
        val match = RuleEngine.shouldBlock(context, fixturePackage)
        Prefs.allowPackageUntil(context, fixturePackage, System.currentTimeMillis() + 1800, match.policyToken)
        openFixture(true)
        assertTrue("Keyboard must not suppress break-expiry enforcement", blocked())
    }
    @Test fun disabledRuleAllowsActualApp() {
        configure()
        Prefs.savePlans(context, Prefs.getPlans(context).map { it.copy(enabled = false) })
        openFixture()
        assertTrue(device.wait(Until.hasObject(By.text("Fixture distraction")), 5000))
        assertFalse(device.hasObject(By.text("Blocked")))
    }
}
