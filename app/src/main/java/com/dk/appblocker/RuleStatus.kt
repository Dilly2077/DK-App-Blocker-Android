package com.dk.appblocker

enum class RuleFilter(val label: String) { ALL("All"), ENABLED("Enabled"), ACTIVE("Active"), DISABLED("Disabled") }
data class RuleStatus(val triggerActive: Boolean, val operational: Boolean, val label: String)

fun ruleStatus(plan: BlockPlan, triggerActive: Boolean, accessibility: Boolean, usage: Boolean,
               locationReady: Boolean): RuleStatus = when {
    !plan.enabled -> RuleStatus(false, false, "Disabled")
    !accessibility -> RuleStatus(triggerActive, false, if (triggerActive) "Trigger active · blocking unavailable" else "Blocking unavailable")
    plan.triggerType == TriggerType.DAILY_LIMIT && !usage -> RuleStatus(false, false, "Needs Usage Access")
    plan.triggerType == TriggerType.LOCATION && !locationReady -> RuleStatus(triggerActive, false, "Location sync needs attention")
    triggerActive -> RuleStatus(true, true, "Enforcing")
    else -> RuleStatus(false, true, "Waiting for trigger")
}

fun RuleFilter.includes(plan: BlockPlan, status: RuleStatus): Boolean = when (this) {
    RuleFilter.ALL -> true
    RuleFilter.ENABLED -> plan.enabled
    RuleFilter.ACTIVE -> plan.enabled && status.triggerActive
    RuleFilter.DISABLED -> !plan.enabled
}
