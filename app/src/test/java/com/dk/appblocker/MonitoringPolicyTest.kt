package com.dk.appblocker

import org.junit.Assert.*
import org.junit.Test

class MonitoringPolicyTest {
    @Test fun allRuleKindsAndFocusContributeOnlyExplicitEnabledNonRecoveryTargets() {
        val plans = TriggerType.entries.map { BlockPlan(triggerType = it, packages = listOf("target.$it", "recovery")) } +
            BlockPlan(packages = listOf("disabled.app"), enabled = false)
        val targets = MonitoringPolicy.targets(plans, setOf("focus.app"), setOf("recovery"))
        assertEquals(5, targets.size); assertFalse("disabled.app" in targets); assertFalse("future.bank" in targets)
    }
    @Test fun registryStoresNoIdentityForUnselectedAppsAndDropsReusedWindowIds() {
        val windows = TargetWindows(); windows.select(setOf("social"))
        windows.observe(1, "bank", 100); assertFalse(windows.hasCandidates)
        windows.observe(1, "social", 100); assertEquals("social", windows.packageFor(1))
        windows.observe(1, "bank", 101); assertNull(windows.packageFor(1))
    }
    @Test fun absentTargetsExpireButVisibleIdentitiesSurviveLongSessions() {
        val windows = TargetWindows(); windows.select(setOf("social")); windows.observe(1, "social", 100)
        windows.reconcile(setOf(1), 10_000_000); assertEquals("social", windows.packageFor(1))
        windows.reconcile(emptySet(), 10_000_001); assertFalse(windows.hasCandidates)
    }
    @Test fun newSelectionAndRemovalInvalidateOldIdentitiesImmediately() {
        val windows = TargetWindows(); windows.select(setOf("a")); windows.observe(1, "a", 1)
        windows.select(setOf("b")); assertNull(windows.packageFor(1))
        windows.observe(2, "b", 2); windows.removed(2); assertFalse(windows.hasCandidates)
    }
}
