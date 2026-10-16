package com.dk.appblocker

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DashboardPolicyTest {
    private val manual = BlockPlan(triggerType = TriggerType.MANUAL, manualActive = true)
    @Test fun activeTriggerIsNotEnforcementWhenAccessibilityIsUnavailable() {
        val status = ruleStatus(manual, true, false, false, false)
        assertTrue(status.triggerActive); assertFalse(status.operational)
        assertEquals("Trigger active · blocking unavailable", status.label)
        assertTrue(RuleFilter.ACTIVE.includes(manual, status))
    }
    @Test fun usageAccessIsRequiredOnlyForDailyLimits() {
        assertEquals("Enforcing", ruleStatus(manual, true, true, false, false).label)
        assertEquals("Needs Usage Access", ruleStatus(manual.copy(triggerType = TriggerType.DAILY_LIMIT), true, true, false, true).label)
        assertEquals("Location sync needs attention", ruleStatus(manual.copy(triggerType = TriggerType.LOCATION), true, true, true, false).label)
    }
    @Test fun filtersDistinguishEnabledFromTriggerActiveAndDisabled() {
        val waiting = ruleStatus(manual, false, true, true, true)
        assertTrue(RuleFilter.ENABLED.includes(manual, waiting)); assertFalse(RuleFilter.ACTIVE.includes(manual, waiting))
        val disabled = manual.copy(enabled = false)
        assertEquals("Disabled", ruleStatus(disabled, true, true, true, true).label)
        assertTrue(RuleFilter.DISABLED.includes(disabled, waiting))
    }
    @Test fun everyInsightPeriodHasAnAccurateHeadingAndUnambiguousLabels() {
        assertEquals(listOf("Today", "Last 7 days", "Last 30 days"), InsightPeriod.entries.map { it.heading })
        val week = attemptBuckets(InsightPeriod.WEEK, emptyList(), LocalDate.of(2026, 10, 16))
        assertEquals(7, week.size); assertEquals(7, week.map { it.label }.distinct().size)
        assertTrue(week.all { it.label.length == 3 })
    }
    @Test fun chartsCountOnlyRecordedEventsInTheirPeriodAcrossMonthAndDstBoundaries() {
        val today = LocalDate.of(2026, 10, 26); val zone = ZoneId.of("Europe/London")
        fun event(day: LocalDate) = BlockEvent(day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), "example.app", "Recorded")
        val events = listOf(event(today), event(today.minusDays(1)), event(today.minusDays(8)), event(today.minusDays(29)), event(today.minusDays(30)))
        assertEquals(1L, attemptBuckets(InsightPeriod.DAY, events, today, zone).sumOf { it.count })
        assertEquals(2L, attemptBuckets(InsightPeriod.WEEK, events, today, zone).sumOf { it.count })
        assertEquals(4L, attemptBuckets(InsightPeriod.MONTH, events, today, zone).sumOf { it.count })
        assertTrue(attemptBuckets(InsightPeriod.MONTH, emptyList(), today, zone).all { it.count == 0L })
    }
}
