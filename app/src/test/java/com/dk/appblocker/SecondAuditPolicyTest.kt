package com.dk.appblocker

import org.junit.Assert.*
import org.junit.Test

class SecondAuditPolicyTest {
    private val display = PaneProtection.Bounds(0, 24, 1000, 960)
    @Test fun settingsFocusedInOtherPaneDoesNotExemptBlockedPane() {
        val blocked = PaneProtection.Bounds(0, 500, 1000, 1000)
        val settings = PaneProtection.Bounds(0, 0, 1000, 490)
        assertEquals(listOf(PaneProtection.Bounds(0, 500, 1000, 960)), PaneProtection.regions(blocked, listOf(settings), display))
    }
    @Test fun recoveryWindowsAreNeverCoveredEvenWhenBoundsOverlap() {
        val pieces = PaneProtection.regions(PaneProtection.Bounds(0, 0, 1000, 1000),
            listOf(PaneProtection.Bounds(0, 0, 1000, 500)), display)
        assertEquals(listOf(PaneProtection.Bounds(0, 500, 1000, 960)), pieces)
    }
    @Test fun fullscreenRecoveryOrHomeRemovesAllBlockingSurfaces() {
        assertTrue(PaneProtection.regions(display, listOf(display), display).isEmpty())
    }
    @Test fun arbitraryRecoveryLayoutCannotBeCovered() {
        val random = java.util.Random(42)
        repeat(500) {
            fun rect(): PaneProtection.Bounds {
                val left = random.nextInt(800); val top = random.nextInt(800)
                return PaneProtection.Bounds(left, top, left + 1 + random.nextInt(500), top + 1 + random.nextInt(500))
            }
            val blocked = rect(); val recovery = List(4) { rect() }
            PaneProtection.regions(blocked, recovery, display).forEach { piece ->
                assertEquals(piece, piece.intersect(blocked)); assertEquals(piece, piece.intersect(display))
                recovery.forEach { assertNull(piece.intersect(it)) }
            }
        }
    }
    @Test fun individualStrictRuleNeedsPasswordWhenGlobalModeIsOff() {
        assertEquals(BlockingPolicy.BreakGate.PASSWORD, BlockingPolicy.breakGate(true, StrictSettings(pinHash = "configured", enabled = false)))
        assertEquals(BlockingPolicy.BreakGate.PASSWORD_SETUP, BlockingPolicy.breakGate(true, StrictSettings()))
        assertEquals(BlockingPolicy.BreakGate.FREE, BlockingPolicy.breakGate(false, StrictSettings()))
    }
    @Test fun activeGlobalTimerAndBiometricPoliciesStillApply() {
        assertEquals(BlockingPolicy.BreakGate.TIMER, BlockingPolicy.breakGate(true, StrictSettings(enabled = true, unlockMethod = "TIMER")))
        assertEquals(BlockingPolicy.BreakGate.BIOMETRIC, BlockingPolicy.breakGate(false, StrictSettings(enabled = true, unlockMethod = "BIOMETRIC")))
    }
    @Test fun missingPauseIsClosedByAStopOrPackageLevelBackgroundEvent() {
        val events = listOf(UsageAccumulator.Transition("a", "one", 0, true), UsageAccumulator.Transition("a", "", 100, false))
        assertEquals(100L, UsageAccumulator.totals(events, 0, 1000)["a"])
    }
    @Test fun legacySingleResumeClosesAppWhenAnotherResumesDespiteMissingPause() {
        val events = listOf(UsageAccumulator.Transition("a", "one", 0, true), UsageAccumulator.Transition("b", "two", 100, true))
        assertEquals(100L, UsageAccumulator.totals(events, 0, 1000, singleResume = true)["a"])
        assertEquals(1000L, UsageAccumulator.totals(events, 0, 1000, singleResume = false)["a"])
    }
    @Test fun longRunningSessionAndConcurrentMultiWindowUsageAreClippedToRequestedDay() {
        val events = listOf(UsageAccumulator.Transition("a", "one", 0, true), UsageAccumulator.Transition("b", "two", 100_000_000, true))
        val totals = UsageAccumulator.totals(events, 100_000_000, 100_060_000)
        assertEquals(60_000L, totals["a"]); assertEquals(60_000L, totals["b"])
        assertEquals(120_000L, totals.values.sum())
    }
    @Test fun entirelyMissingHistoryDoesNotInventUsage() {
        assertTrue(UsageAccumulator.totals(emptyList(), 0, 1000).isEmpty())
        assertTrue(UsageAccumulator.totals(listOf(UsageAccumulator.Transition("a", "one", 100, false)), 0, 1000).isEmpty())
    }
    @Test fun usageSamplesShareQueriesAcrossManyRulesAndExpireAtBoundary() {
        val cache = UsageSampleCache()
        repeat(100) { assertEquals(100L, cache.get("today:boot1", 1000) { mapOf("a" to 100L) }["a"]) }
        assertEquals(1L, cache.loads)
        cache.get("today:boot1", 5999) { fail("Early cache refresh"); emptyMap() }
        cache.get("today:boot1", 6000) { emptyMap() }
        assertEquals(2L, cache.loads)
    }
    @Test fun midnightRebootClockResetAndPermissionLossInvalidateSamples() {
        val cache = UsageSampleCache()
        cache.get("day1:boot1", 100) { emptyMap() }
        cache.get("day2:boot1", 101) { emptyMap() }
        cache.get("day2:boot2", 102) { emptyMap() }
        cache.get("day2:boot2", 1) { emptyMap() }
        cache.invalidate(); cache.get("day2:boot2", 2) { emptyMap() }
        assertEquals(5L, cache.loads)
    }
}
