package com.dk.appblocker

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrefsAndRulesTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val pkg = "com.example.distraction"
    private fun manual(breaks: Boolean = true, strict: Boolean = false) = BlockPlan(triggerType = TriggerType.MANUAL, packages = listOf(pkg), manualActive = true, allowBreaks = breaks, strict = strict)
    @Before fun reset() { context.getSharedPreferences("dk_blocker_prefs", 0).edit().clear().commit() }

    @Test fun legacyCredentialMigratesOnlyAfterSuccessfulVerification() {
        val legacy = MessageDigest.getInstance("SHA-256").digest("1234".toByteArray()).joinToString("") { "%02x".format(it) }
        Prefs.saveStrict(context, StrictSettings(pinHash = legacy))
        assertFalse(Prefs.checkPin(context, "wrong")); assertEquals(legacy, Prefs.getStrict(context).pinHash)
        assertTrue(Prefs.checkPin(context, "1234")); assertFalse(CredentialHasher.needsMigration(Prefs.getStrict(context).pinHash))
    }
    @Test fun configuredPasswordCannotBeReplacedWithoutCurrentPassword() {
        assertTrue(Prefs.replacePin(context, "", "original"))
        assertFalse(Prefs.replacePin(context, "wrong", "replacement"))
        assertTrue(Prefs.checkPin(context, "original"))
        assertTrue(Prefs.replacePin(context, "original", "replacement"))
        assertTrue(Prefs.checkPin(context, "replacement"))
    }
    @Test fun authenticationFailuresPersistAcrossContextsAndRateLimit() {
        Prefs.replacePin(context, "", "1234")
        repeat(5) { assertFalse(Prefs.checkPin(context, "bad")) }
        assertFalse(Prefs.checkPin(context.createConfigurationContext(context.resources.configuration), "1234"))
    }
    @Test fun manualDisabledAndAllowedAppsBehaveCorrectly() {
        val plan = manual()
        Prefs.savePlans(context, listOf(plan))
        assertTrue(RuleEngine.shouldBlock(context, pkg).blocked)
        assertFalse(RuleEngine.shouldBlock(context, "com.example.allowed").blocked)
        Prefs.savePlans(context, listOf(plan.copy(enabled = false)))
        assertFalse(RuleEngine.shouldBlock(context, pkg).blocked)
    }
    @Test fun recoveryPackagesStayAllowedEvenWhenExplicitlyConfigured() {
        listOf(context.packageName, "com.android.settings", "com.android.systemui", "com.android.permissioncontroller").forEach {
            Prefs.savePlans(context, listOf(manual().copy(packages = listOf(it))))
            assertFalse(RuleEngine.shouldBlock(context, it).blocked)
        }
    }
    @Test fun breakCannotOverrideNewOrStricterRule() {
        val easy = manual()
        Prefs.savePlans(context, listOf(easy))
        val match = RuleEngine.shouldBlock(context, pkg)
        Prefs.allowPackageUntil(context, pkg, System.currentTimeMillis() + 300_000, match.policyToken)
        assertFalse(RuleEngine.shouldBlock(context, pkg).blocked)
        Prefs.savePlans(context, listOf(easy, manual(breaks = false, strict = true)))
        val next = RuleEngine.shouldBlock(context, pkg)
        assertTrue(next.blocked); assertFalse(next.allowBreaks); assertTrue(next.strict)
    }
    @Test fun focusPersistsAcrossContextsAndExpiresAfterReboot() {
        Prefs.startFocus(context, setOf(pkg), 25)
        assertTrue(RuleEngine.shouldBlock(context.createConfigurationContext(context.resources.configuration), pkg).blocked)
        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, Prefs.boot(context) + 1)
        assertFalse(RuleEngine.shouldBlock(context, pkg).blocked)
    }
    @Test fun expiredTimerAndLegacyZeroDeadlineRecover() {
        Prefs.saveStrict(context, StrictSettings(enabled = true, unlockMethod = "TIMER", lockUntil = 0L))
        assertFalse(Prefs.getStrict(context).enabled)
        Prefs.saveStrict(context, StrictSettings(enabled = true, unlockMethod = "TIMER", lockUntil = System.currentTimeMillis() + 100_000,
            lockElapsedEnd = SystemClock.elapsedRealtime(), lockBoot = Prefs.boot(context)))
        assertFalse(Prefs.getStrict(context).enabled)
    }
    @Test fun geofenceEnterExitAndRevokedPermissionDoNotLeaveStaleBlocks() {
        val plan = manual().copy(triggerType = TriggerType.LOCATION)
        Prefs.savePlans(context, listOf(plan))
        Prefs.setActiveGeofence(context, plan.id, true)
        assertTrue(plan.id in Prefs.getActiveGeofences(context))
        assertFalse(RuleEngine.shouldBlock(context, pkg).blocked) // No location permission.
        org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        assertTrue(RuleEngine.shouldBlock(context, pkg).blocked)
        Prefs.setActiveGeofence(context, plan.id, false)
        assertTrue(Prefs.getActiveGeofences(context).isEmpty())
        assertFalse(RuleEngine.shouldBlock(context, pkg).blocked)
    }
    @Test fun blockScreenSettingsPersistExactly() {
        val settings = BlockScreenSettings("Focus", "{app}: {reason}", "#07110C", "#8BFFA8", false)
        Prefs.saveBlockScreen(context, settings)
        assertEquals(settings, Prefs.getBlockScreen(context))
    }
    @Test fun exportedConfigurationContainsNoCredential() {
        Prefs.saveStrict(context, StrictSettings(pinHash = "SECRET", enabled = true))
        assertFalse(Prefs.exportJson(context).contains("SECRET"))
    }
    @Test fun malformedPersistenceUsesRecoverableDefaults() {
        context.getSharedPreferences("dk_blocker_prefs", 0).edit().putString("plans", "bad json").putString("strict", "null").commit()
        assertTrue(Prefs.getPlans(context).isEmpty()); assertFalse(Prefs.getStrict(context).enabled)
    }
}
