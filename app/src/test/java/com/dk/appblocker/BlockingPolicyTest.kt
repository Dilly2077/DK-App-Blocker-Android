package com.dk.appblocker

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class BlockingPolicyTest {
    private val monday = LocalDateTime.of(2026, 10, 12, 9, 0)
    private fun active(plan: BlockPlan, at: LocalDateTime) = BlockingPolicy.scheduleActive(plan, at)

    @Test fun scheduleIncludesStartExcludesEnd() {
        val plan = BlockPlan(days = setOf(1), startMinute = 540, endMinute = 600)
        assertFalse(active(plan, monday.minusMinutes(1)))
        assertTrue(active(plan, monday))
        assertTrue(active(plan, monday.plusMinutes(59)))
        assertFalse(active(plan, monday.plusMinutes(60)))
    }
    @Test fun overnightBelongsToStartingWeekday() {
        val plan = BlockPlan(days = setOf(1), startMinute = 22 * 60, endMinute = 6 * 60)
        assertTrue(active(plan, monday.withHour(23)))
        assertTrue(active(plan, monday.plusDays(1).withHour(5)))
        assertFalse(active(plan, monday.withHour(5)))
        assertFalse(active(plan, monday.plusDays(1).withHour(6)))
        assertFalse(active(plan, monday.plusDays(1).withHour(23)))
    }
    @Test fun overnightWrapsSundayToMonday() {
        assertTrue(active(BlockPlan(days = setOf(7), startMinute = 1380, endMinute = 60), monday.withHour(0)))
    }
    @Test fun equalTimesMeansWholeSelectedDay() {
        val plan = BlockPlan(days = setOf(1), startMinute = 0, endMinute = 0)
        assertTrue(active(plan, monday.withHour(23).withMinute(59)))
        assertFalse(active(plan, monday.plusDays(1)))
    }
    @Test fun noWeekdaysNeverActivates() { assertFalse(active(BlockPlan(days = emptySet()), monday)) }
    @Test fun invalidMinutesNeverActivate() { assertFalse(active(BlockPlan(startMinute = -1), monday)) }
    @Test fun overlapUsesMostRestrictiveBreakAndStrictPolicyInAnyOrder() {
        val easy = BlockPlan(strict = false, allowBreaks = true)
        val hard = BlockPlan(strict = true, allowBreaks = false)
        listOf(listOf(easy, hard), listOf(hard, easy)).forEach {
            val match = BlockingPolicy.combine(it, true)
            assertTrue(match.blocked); assertTrue(match.strict); assertFalse(match.allowBreaks)
        }
    }
    @Test fun focusWithoutRulesStillBlocksWithBreaks() {
        val match = BlockingPolicy.combine(emptyList(), true)
        assertTrue(match.blocked); assertTrue(match.allowBreaks)
    }
    @Test fun noMatchAllowsPackage() { assertFalse(BlockingPolicy.combine(emptyList(), false).blocked) }
    @Test fun dailyLimitBlocksAtExactCombinedUsageThreshold() {
        assertFalse(BlockingPolicy.dailyLimitReached(29 * 60_000L + 59_999, 30))
        assertTrue(BlockingPolicy.dailyLimitReached(30 * 60_000L, 30))
        assertTrue(BlockingPolicy.dailyLimitReached(31 * 60_000L, 30))
        assertFalse(BlockingPolicy.dailyLimitReached(0, 0))
    }
    @Test fun deadlineSurvivesProcessRestartAndIgnoresWallClock() {
        val deadline = Deadline(100_000, 5000, 4)
        assertEquals(2000L, deadline.copy().remaining(3000, 4))
        assertEquals(2000L, deadline.copy(wallEnd = -999_999).remaining(3000, 4))
    }
    @Test fun deadlineExpiresAtBoundary() { assertEquals(0L, Deadline(1000, 5000, 4).remaining(5000, 4)) }
    @Test fun rebootExpiresRatherThanTrapsUser() { assertEquals(0L, Deadline(1000, 5000, 4).remaining(10, 5)) }
    @Test fun usageClipsSessionSpanningMidnight() {
        val events = listOf(UsageAccumulator.Transition("a", "one", 50, true), UsageAccumulator.Transition("a", "one", 150, false))
        assertEquals(50L, UsageAccumulator.totals(events, 100, 200)["a"])
    }
    @Test fun repeatedResumeDoesNotResetOrDoubleCount() {
        val events = listOf(UsageAccumulator.Transition("a", "one", 100, true), UsageAccumulator.Transition("a", "one", 110, true))
        assertEquals(100L, UsageAccumulator.totals(events, 100, 200)["a"])
    }
    @Test fun multipleActivitiesMergeIntervalsPerPackage() {
        val events = listOf(UsageAccumulator.Transition("a", "one", 100, true), UsageAccumulator.Transition("a", "two", 120, true), UsageAccumulator.Transition("a", "one", 150, false))
        assertEquals(100L, UsageAccumulator.totals(events, 100, 200)["a"])
    }
    @Test fun screenOffAndShutdownCloseSessions() {
        val events = listOf(UsageAccumulator.Transition("a", "one", 100, true), UsageAccumulator.Transition("", "", 150, false))
        assertEquals(50L, UsageAccumulator.totals(events, 100, 200)["a"])
    }
    @Test fun combinedUsageSumsSelectedPackages() {
        val events = listOf(UsageAccumulator.Transition("a", "one", 100, true), UsageAccumulator.Transition("b", "one", 150, true))
        assertEquals(150L, UsageAccumulator.totals(events, 100, 200).values.sum())
    }
}
