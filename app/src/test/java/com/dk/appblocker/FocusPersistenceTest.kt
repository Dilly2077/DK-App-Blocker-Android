package com.dk.appblocker

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FocusPersistenceTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val pkg = "example.distraction"
    private val prefs get() = context.getSharedPreferences("dk_blocker_prefs", 0)
    @Before fun reset() {
        prefs.edit().clear().commit()
        shadowOf(context.getSystemService(android.app.AppOpsManager::class.java)).setMode(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName, android.app.AppOpsManager.MODE_IGNORED)
    }

    @Test fun longSessionIsDurableAsOneValueAndRestoresWithoutAnInMemoryTimer() {
        assertEquals(FocusStartResult.STARTED, Prefs.startFocus(context, setOf(pkg), 1500))
        val session = Prefs.focusSession(context)!!
        assertEquals(90_000_000L, session.durationMillis)
        val disk = java.io.File(context.applicationInfo.dataDir, "shared_prefs/dk_blocker_prefs.xml").readText()
        assertTrue(disk.contains("focus_session")); assertTrue(disk.contains(pkg)); assertTrue(disk.contains(session.id))
        ShadowSystemClock.advanceBy(Duration.ofHours(2))
        val recreated = context.createConfigurationContext(context.resources.configuration)
        assertEquals(session.id, Prefs.focusSession(recreated)!!.id)
        assertEquals(23 * 60 * 60_000L, Prefs.focusSession(recreated)!!.remaining(SystemClock.elapsedRealtime(), Prefs.boot(context)))
        assertTrue(RuleEngine.shouldBlock(recreated, pkg).blocked)
    }
    @Test fun shortSessionExpiresWithoutHomeAndClearsActivePackages() {
        Prefs.startFocus(context, setOf(pkg), 5)
        assertTrue(RuleEngine.shouldBlock(context, pkg).blocked)
        ShadowSystemClock.advanceBy(Duration.ofMinutes(5))
        assertFalse(RuleEngine.shouldBlock(context, pkg).blocked)
        assertNull(Prefs.focusSession(context)); assertTrue(Prefs.focusPackages(context).isEmpty())
        assertFalse(prefs.contains("focus_session"))
        assertEquals(setOf(pkg), Prefs.focusSelection(context))
    }
    @Test fun expiryPreservesEachIndependentRuleTypeAndItsRestrictions() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        for (type in TriggerType.entries) {
            prefs.edit().clear().commit()
            Prefs.startFocusSession(context, setOf(pkg), 1000)
            val rule = BlockPlan(name = "Independent $type", triggerType = type, packages = listOf(pkg), manualActive = true,
                days = (1..7).toSet(), startMinute = 0, endMinute = 0, strict = true, allowBreaks = false)
            if (type == TriggerType.DAILY_LIMIT) {
                // Missing Usage Access must keep this rule inactive, even while focus is active.
                assertFalse(RuleEngine.isPlanActive(context, rule))
                assertTrue(BlockingPolicy.combine(listOf(rule), false).blocked)
                continue
            }
            Prefs.savePlans(context, listOf(rule)); Prefs.setActiveGeofence(context, rule.id, true)
            ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
            val match = RuleEngine.shouldBlock(context, pkg)
            assertTrue("$type must remain blocked", match.blocked)
            assertTrue(match.strict); assertFalse(match.allowBreaks); assertEquals(rule.id, match.plan!!.id)
        }
    }
    @Test fun simultaneousStartsCannotReplaceOrExtendAnActiveSession() {
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = executor.invokeAll(listOf(java.util.concurrent.Callable { Prefs.startFocus(context, setOf(pkg), 5) },
                java.util.concurrent.Callable { Prefs.startFocus(context, setOf("other.app"), 1500) })).map { it.get() }
            assertEquals(1, results.count { it == FocusStartResult.STARTED })
            assertEquals(1, results.count { it == FocusStartResult.ALREADY_ACTIVE })
            val original = Prefs.focusSession(context)!!
            Prefs.startFocus(context, setOf("third.app"), 60)
            assertEquals(original, Prefs.focusSession(context))
        } finally { executor.shutdownNow() }
    }
    @Test fun reachedDailyLimitStillBlocksAfterFocusExpires() {
        SystemClock.setCurrentTimeMillis(startOfTodayMillis() + 12 * 60 * 60_000L)
        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, Prefs.boot(context) + 1)
        val ops = context.getSystemService(android.app.AppOpsManager::class.java)
        shadowOf(ops).setMode(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName,
            android.app.AppOpsManager.MODE_ALLOWED)
        val usage = shadowOf(context.getSystemService(android.app.usage.UsageStatsManager::class.java))
        usage.addEvent(pkg, System.currentTimeMillis() - 120_000, android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND)
        usage.addEvent(pkg, System.currentTimeMillis() - 1, android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND)
        val rule = BlockPlan(name = "Daily", triggerType = TriggerType.DAILY_LIMIT, packages = listOf(pkg), dailyLimitMinutes = 1)
        Prefs.savePlans(context, listOf(rule))
        Prefs.startFocusSession(context, setOf(pkg), 1000)
        assertTrue(RuleEngine.shouldBlock(context, pkg).blocked)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        assertNull(Prefs.focusSession(context))
        assertEquals(rule.id, RuleEngine.shouldBlock(context, pkg).plan!!.id)
    }
    @Test fun futureSelectionCannotChangeCurrentBlocklistAndPersistsAfterEnd() {
        Prefs.startFocus(context, setOf(pkg), 5)
        Prefs.saveFocusSelection(context, setOf("future.app"))
        assertEquals(setOf(pkg), Prefs.focusPackages(context))
        assertFalse(RuleEngine.shouldBlock(context, "future.app").blocked)
        assertTrue(Prefs.stopFocus(context))
        assertEquals(setOf("future.app"), Prefs.focusSelection(context))
    }
    @Test fun screenOffDeepSleepCountsTowardExpiryWithoutUiTicks() {
        Prefs.startFocus(context, setOf(pkg), 5)
        ShadowSystemClock.simulateDeepSleep(Duration.ofMinutes(5))
        assertNull(Prefs.focusSession(context))
        assertFalse(RuleEngine.shouldBlock(context, pkg).blocked)
    }
    @Test fun expiredLegacySessionKeepsItsSelectionForTheNextSession() {
        prefs.edit().putString("focus_deadline", Gson().toJson(Deadline(0, 0, Prefs.boot(context))))
            .putStringSet("focus_packages", setOf(pkg)).commit()
        assertNull(Prefs.focusSession(context))
        assertEquals(setOf(pkg), Prefs.focusSelection(context))
    }
    @Test fun strictModeGatesManualStartAndEndButNeverAutomaticExpiry() {
        Prefs.startFocusSession(context, setOf(pkg), 1000)
        Prefs.saveStrict(context, StrictSettings(enabled = true, unlockMethod = "TIMER", lockUntil = System.currentTimeMillis() + 60_000,
            lockElapsedEnd = SystemClock.elapsedRealtime() + 60_000, lockBoot = Prefs.boot(context)))
        assertFalse(Prefs.stopFocus(context))
        assertEquals(FocusStartResult.LOCKED, Prefs.startFocus(context, setOf(pkg), 10))
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        assertFalse(RuleEngine.shouldBlock(context, pkg).blocked)
        assertTrue(Prefs.getStrict(context).enabled)
    }
    @Test fun rebootExpiresLongSessionAndLeavesIndependentRulesAndSelection() {
        Prefs.startFocus(context, setOf(pkg), 3000)
        Prefs.savePlans(context, listOf(BlockPlan(triggerType = TriggerType.MANUAL, packages = listOf(pkg), manualActive = true)))
        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, Prefs.boot(context) + 1)
        assertNull(Prefs.focusSession(context)); assertTrue(RuleEngine.shouldBlock(context, pkg).blocked)
        assertEquals(setOf(pkg), Prefs.focusSelection(context))
    }
    @Test fun wallClockMetadataJumpsDoNotChangePersistedElapsedDeadline() {
        Prefs.startFocus(context, setOf(pkg), 5)
        val before = Prefs.focusSession(context)!!
        // Model wall/elapsed divergence in both directions without modifying the host PC's clock.
        for (offset in listOf(-7 * 86_400_000L, 7 * 86_400_000L)) {
            val shifted = before.copy(deadline = before.deadline.copy(wallEnd = before.deadline.wallEnd + offset))
            prefs.edit().putString("focus_session", Gson().toJson(shifted)).commit()
            assertEquals(before.deadline.elapsedEnd, Prefs.focusSession(context)!!.deadline.elapsedEnd)
            assertEquals(before.remaining(SystemClock.elapsedRealtime(), Prefs.boot(context)),
                Prefs.focusSession(context)!!.remaining(SystemClock.elapsedRealtime(), Prefs.boot(context)))
            assertTrue(RuleEngine.shouldBlock(context, pkg).blocked)
        }
    }
    @Test fun legacySessionMigratesWithoutExtendingItsElapsedDeadline() {
        val deadline = Deadline(System.currentTimeMillis() + 60_000, SystemClock.elapsedRealtime() + 60_000, Prefs.boot(context))
        prefs.edit().putString("focus_deadline", Gson().toJson(deadline)).putStringSet("focus_packages", setOf(pkg)).commit()
        val migrated = Prefs.focusSession(context)!!
        assertEquals(deadline.elapsedEnd, migrated.deadline.elapsedEnd)
        assertEquals(setOf(pkg), migrated.packages)
        assertFalse(prefs.contains("focus_deadline")); assertTrue(prefs.contains("focus_session"))
    }
    @Test fun invalidOverflowEmptyAndRecoveryOnlySessionsDoNotPersist() {
        assertEquals(FocusStartResult.INVALID_DURATION, Prefs.startFocusSession(context, setOf(pkg), Long.MAX_VALUE))
        assertEquals(FocusStartResult.INVALID_DURATION, Prefs.startFocus(context, setOf(pkg), -1))
        assertEquals(FocusStartResult.NO_APPS, Prefs.startFocus(context, emptySet(), 5))
        assertEquals(FocusStartResult.NO_APPS, Prefs.startFocus(context, setOf("com.android.settings"), 5))
        assertNull(Prefs.focusSession(context))
    }
    @Test fun corruptSessionRecoversAndDoesNotResurrectLegacyState() {
        prefs.edit().putString("focus_session", "{\"id\":\"x\"}").commit()
        assertNull(Prefs.focusSession(context)); assertFalse(prefs.contains("focus_session"))
    }
    @Test fun shortBreakDeadlinesRemainCappedAt24Hours() {
        Prefs.startFocus(context, setOf(pkg), 3000)
        Prefs.allowPackageUntil(context, pkg, System.currentTimeMillis() + 100 * 86_400_000L)
        assertTrue(Prefs.packageAllowedUntil(context, pkg) - System.currentTimeMillis() <= 86_400_000L)
    }
}
