package com.dk.appblocker

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PolicyPersistenceTest {
    private val base: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var context: FailingStorage
    private var syncs = 0
    private val rule = BlockPlan(name = "Original", triggerType = TriggerType.MANUAL, packages = listOf("example.app"), manualActive = true)
    @Before fun reset() {
        base.getSharedPreferences("dk_blocker_prefs", 0).edit().clear().commit()
        context = FailingStorage(base); syncs = 0
        Prefs.savePlans(context, listOf(rule))
    }
    private fun repository() = RuleRepository(context) { syncs++ }
    @Test fun duplicateCopiesAllSettingsWithAFreshIdAndDeleteRemovesOnlyThatRule() {
        assertEquals(PolicyWrite.SAVED, repository().perform(rule.id, RuleAction.DUPLICATE))
        val copy = Prefs.getPlans(context).last()
        assertNotEquals(rule.id, copy.id); assertEquals(rule.copy(id = copy.id, name = "Original (copy)"), copy)
        assertEquals(PolicyWrite.SAVED, repository().perform(rule.id, RuleAction.DELETE))
        assertEquals(listOf(copy), Prefs.getPlans(context)); assertEquals(2, syncs)
    }
    @Test fun everyRuleMutationIsRejectedDuringStrictMode() {
        Prefs.saveStrict(context, StrictSettings(enabled = true, unlockMethod = "TIMER", lockUntil = System.currentTimeMillis() + 60_000,
            lockElapsedEnd = android.os.SystemClock.elapsedRealtime() + 60_000, lockBoot = Prefs.boot(context)))
        RuleAction.entries.forEach { assertEquals(PolicyWrite.LOCKED, repository().perform(rule.id, it)) }
        assertEquals(PolicyWrite.LOCKED, repository().save(rule.copy(name = "Changed")))
        assertEquals(PolicyWrite.LOCKED, repository().save(rule.copy(id = "new")))
        assertEquals(listOf(rule), Prefs.getPlans(context)); assertEquals(0, syncs)
    }
    @Test fun failedEditToggleDuplicateAndDeleteRestoreMemoryAndDoNotSync() {
        context.failWrites = true
        RuleAction.entries.forEach {
            assertEquals(PolicyWrite.STORAGE_ERROR, repository().perform(rule.id, it))
            assertEquals(listOf(rule), Prefs.getPlans(context))
        }
        assertEquals(PolicyWrite.STORAGE_ERROR, repository().save(rule.copy(name = "Lost edit")))
        assertEquals(listOf(rule), Prefs.getPlans(context)); assertEquals(0, syncs)
    }
    @Test fun deletingLocationAtomicallyRemovesOccupancyAndQueuesRegistrationCleanup() {
        val location = rule.copy(triggerType = TriggerType.LOCATION, latitude = 51.5, longitude = -.1, locationConfigured = true)
        Prefs.savePlans(context, listOf(location)); Prefs.setActiveGeofence(context, location.id, true)
        Prefs.finishGeofenceSync(context, listOf(GeofenceSpec.from(location)))
        assertEquals(PolicyWrite.SAVED, repository().perform(location.id, RuleAction.DELETE))
        assertTrue(Prefs.getActiveGeofences(context).isEmpty())
        assertTrue(GeofenceSpec.from(location).registrationId in Prefs.pendingGeofenceRemovals(context))
        assertEquals(1, syncs)
    }
    @Test fun failedLocationDeletePreservesOccupancyAndRegistrationState() {
        val location = rule.copy(triggerType = TriggerType.LOCATION, latitude = 51.5, locationConfigured = true)
        Prefs.savePlans(context, listOf(location)); Prefs.setActiveGeofence(context, location.id, true)
        context.failWrites = true
        assertEquals(PolicyWrite.STORAGE_ERROR, repository().perform(location.id, RuleAction.DELETE))
        assertEquals(setOf(location.id), Prefs.getActiveGeofences(context)); assertEquals(0, syncs)
    }
    @Test fun locationUpdatesKeepUnchangedOccupancyAndRejectLateCallbacksForChangedOrDeletedRules() {
        val location = rule.copy(triggerType = TriggerType.LOCATION, latitude = 51.5, locationConfigured = true)
        Prefs.savePlans(context, listOf(location))
        val oldId = GeofenceSpec.from(location).registrationId
        Prefs.setActiveGeofence(context, location.id, true, oldId)
        Prefs.savePlans(context, listOf(location.copy(name = "Renamed")))
        assertEquals(setOf(location.id), Prefs.getActiveGeofences(context))
        val changed = location.copy(latitude = 52.0)
        Prefs.savePlans(context, listOf(changed))
        assertTrue(Prefs.getActiveGeofences(context).isEmpty())
        Prefs.setActiveGeofence(context, changed.id, true, oldId)
        assertTrue(Prefs.getActiveGeofences(context).isEmpty())
        Prefs.setActiveGeofence(context, changed.id, true, GeofenceSpec.from(changed).registrationId)
        assertEquals(setOf(changed.id), Prefs.getActiveGeofences(context))
        Prefs.setActiveGeofence(context, changed.id, false, oldId)
        assertEquals(setOf(changed.id), Prefs.getActiveGeofences(context))
        Prefs.savePlans(context, listOf(changed.copy(enabled = false)))
        Prefs.setActiveGeofence(context, changed.id, true, GeofenceSpec.from(changed).registrationId)
        assertTrue(Prefs.getActiveGeofences(context).isEmpty())
        Prefs.savePlans(context, emptyList())
        Prefs.setActiveGeofence(context, changed.id, true, GeofenceSpec.from(changed).registrationId)
        assertTrue(Prefs.getActiveGeofences(context).isEmpty())
    }
    @Test fun unavailableLocationEventsInvalidateRegistrationMetadataForARealRetry() {
        val location = rule.copy(triggerType = TriggerType.LOCATION, latitude = 51.5, locationConfigured = true)
        Prefs.savePlans(context, listOf(location)); Prefs.setActiveGeofence(context, location.id, true)
        Prefs.finishGeofenceSync(context, listOf(GeofenceSpec.from(location))); Prefs.setGeofenceFingerprint(context, "confirmed")
        assertTrue(Prefs.invalidateGeofenceRegistrations(context))
        assertTrue(Prefs.registeredGeofences(context).isEmpty()); assertTrue(Prefs.getActiveGeofences(context).isEmpty())
        assertNull(Prefs.geofenceFingerprint(context)); assertEquals(-1, Prefs.geofenceRegistrationBoot(context))
    }
    @Test fun failedStrictActivationNeverBecomesVisibleOrPermissive() {
        Prefs.saveStrict(context, StrictSettings(unlockMethod = "TIMER"))
        context.failWrites = true
        assertEquals(PolicyWrite.STORAGE_ERROR, Prefs.activateStrict(context))
        assertFalse(Prefs.getStrict(context).enabled)
    }
    @Test fun failedExpiryCleanupRemainsRecoverableButIsNotReportedAsASavedDisable() {
        Prefs.saveStrict(context, StrictSettings(enabled = true, unlockMethod = "TIMER", lockUntil = 0L,
            lockElapsedEnd = 0L, lockBoot = Prefs.boot(context)))
        context.failWrites = true
        val snapshot = Prefs.strictSnapshot(context)
        assertFalse(snapshot.settings.enabled); assertTrue(snapshot.recoveryPending)
        assertTrue(Prefs.canEditRules(context))
        context.failWrites = false
        assertFalse(Prefs.strictSnapshot(context).recoveryPending)
    }
    @Test fun failedVerifiedDisablePreservesPasswordProtectionAndCooldownState() {
        assertTrue(Prefs.replacePin(context, "", "recovery"))
        assertEquals(PolicyWrite.SAVED, Prefs.activateStrict(context))
        val before = Prefs.getStrict(context)
        context.failWrites = true
        assertFalse(Prefs.disableStrictDurably(context))
        assertEquals(before, Prefs.getStrict(context)); assertFalse(Prefs.canEditRules(context))
        assertEquals(0L, Prefs.editLockedUntil(context))
    }
    @Test fun successfulVerifiedDisableStoresItsCooldownWithTheDisabledPolicy() {
        Prefs.replacePin(context, "", "recovery")
        Prefs.configureStrict(context, Prefs.getStrict(context).copy(editCooldownMinutes = 10))
        assertEquals(PolicyWrite.SAVED, Prefs.activateStrict(context))
        assertTrue(Prefs.disableStrictDurably(context))
        val recreated = context.createConfigurationContext(context.resources.configuration)
        assertFalse(Prefs.getStrict(recreated).enabled)
        assertFalse(Prefs.canEditRules(recreated))
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMinutes(10))
        assertTrue(Prefs.canEditRules(recreated))
    }
    @Test fun failedReconfigurationOrPasswordChangeDoesNotReplaceCredentials() {
        Prefs.replacePin(context, "", "original")
        val before = Prefs.getStrict(context)
        context.failWrites = true
        assertEquals(PolicyWrite.STORAGE_ERROR, Prefs.configureStrict(context, before.copy(unlockMethod = "TIMER")))
        assertFalse(Prefs.replacePin(context, "original", "replacement"))
        assertEquals(before, Prefs.getStrict(context))
    }
    @Test fun staleDraftCannotOverwriteTheRecoveryPassword() {
        val stale = Prefs.getStrict(context)
        Prefs.replacePin(context, "", "new-password")
        val hash = Prefs.getStrict(context).pinHash
        assertEquals(PolicyWrite.SAVED, Prefs.configureStrict(context, stale.copy(timerDurationMinutes = 90)))
        assertEquals(hash, Prefs.getStrict(context).pinHash)
        assertEquals(PolicyWrite.SAVED, Prefs.activateStrict(context))
        assertEquals(PolicyWrite.LOCKED, Prefs.configureStrict(context, stale))
    }
    @Test fun failedFocusAndAppearanceWritesDoNotChangeTheActiveState() {
        context.failWrites = true
        assertEquals(FocusStartResult.STORAGE_ERROR, Prefs.startFocus(context, setOf("example.app"), 5))
        assertNull(Prefs.focusSession(context))
        assertFalse(Prefs.saveBlockScreen(context, BlockScreenSettings(title = "Not stored")))
        assertEquals(BlockScreenSettings(), Prefs.getBlockScreen(context))
        context.failWrites = false
        Prefs.startFocus(context, setOf("example.app"), 5)
        val before = Prefs.focusSession(context)
        context.failWrites = true
        assertFalse(Prefs.stopFocus(context)); assertEquals(before, Prefs.focusSession(context))
    }
}
