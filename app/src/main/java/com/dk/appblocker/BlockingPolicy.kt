package com.dk.appblocker

import java.time.LocalDateTime

/** Pure policy shared by enforcement and tests. */
object BlockingPolicy {
    enum class BreakGate { FREE, PASSWORD, BIOMETRIC, TIMER, PASSWORD_SETUP }
    fun breakGate(ruleStrict: Boolean, settings: StrictSettings): BreakGate {
        if (settings.enabled) return when (settings.unlockMethod) {
            "TIMER" -> BreakGate.TIMER
            "BIOMETRIC" -> BreakGate.BIOMETRIC
            else -> if (settings.pinHash.isNotBlank()) BreakGate.PASSWORD else BreakGate.PASSWORD_SETUP
        }
        return if (!ruleStrict) BreakGate.FREE
            else if (settings.pinHash.isNotBlank()) BreakGate.PASSWORD else BreakGate.PASSWORD_SETUP
    }
    fun dailyLimitReached(usedMillis: Long, limitMinutes: Int): Boolean =
        limitMinutes > 0 && usedMillis >= limitMinutes.toLong() * 60_000L
    fun scheduleActive(plan: BlockPlan, now: LocalDateTime): Boolean {
        if (plan.startMinute !in 0..1439 || plan.endMinute !in 0..1439) return false
        val day = now.dayOfWeek.value
        val minute = now.hour * 60 + now.minute
        if (plan.startMinute == plan.endMinute) return day in plan.days
        if (plan.startMinute < plan.endMinute) return day in plan.days && minute in plan.startMinute until plan.endMinute
        val previousDay = if (day == 1) 7 else day - 1
        return (day in plan.days && minute >= plan.startMinute) || (previousDay in plan.days && minute < plan.endMinute)
    }

    fun combine(plans: List<BlockPlan>, focus: Boolean): RuleEngine.Match {
        if (plans.isEmpty() && !focus) return RuleEngine.Match(false)
        val primary = plans.firstOrNull { !it.allowBreaks } ?: plans.firstOrNull { it.strict } ?: plans.firstOrNull()
        return RuleEngine.Match(true, primary?.let(::reason) ?: "Focus session", primary,
            strict = plans.any { it.strict }, allowBreaks = plans.all { it.allowBreaks })
    }

    private fun reason(plan: BlockPlan): String = when (plan.triggerType) {
        TriggerType.SCHEDULE -> "${plan.name} · scheduled block"
        TriggerType.DAILY_LIMIT -> "${plan.name} · ${plan.dailyLimitMinutes}m daily limit reached"
        TriggerType.LOCATION -> "${plan.name} · location block"
        TriggerType.MANUAL -> "${plan.name} · focus block"
    }
}

/** Elapsed time survives process death and wall-clock changes; reboot safely expires temporary locks. */
data class Deadline(val wallEnd: Long, val elapsedEnd: Long, val boot: Int) {
    fun remaining(elapsedNow: Long, bootNow: Int): Long =
        if (boot != bootNow) 0 else (elapsedEnd - elapsedNow).coerceAtLeast(0)
}

/** Tracks individual resumed activities, clips at midnight and avoids duplicate resume overcount. */
object UsageAccumulator {
    data class Transition(val pkg: String, val activity: String, val at: Long, val resumed: Boolean)
    fun totals(events: List<Transition>, start: Long, end: Long, singleResume: Boolean = false): Map<String, Long> {
        val active = mutableMapOf<Pair<String, String>, Long>()
        val intervals = mutableMapOf<String, MutableList<Pair<Long, Long>>>()
        fun close(key: Pair<String, String>, at: Long) {
            val from = active.remove(key) ?: return
            val left = from.coerceAtLeast(start)
            val right = at.coerceAtMost(end)
            if (right > left) intervals.getOrPut(key.first) { mutableListOf() }.add(left to right)
        }
        events.sortedBy { it.at }.forEach { event ->
            if (event.at > end) return@forEach
            if (event.pkg.isEmpty()) active.keys.toList().forEach { close(it, event.at) }
            else {
                val key = event.pkg to event.activity
                if (event.resumed) {
                    if (singleResume) active.keys.filter { it != key }.toList().forEach { close(it, event.at) }
                    active.putIfAbsent(key, event.at)
                } else if (event.activity.isEmpty()) {
                    active.keys.filter { it.first == event.pkg }.toList().forEach { close(it, event.at) }
                } else close(key, event.at)
            }
        }
        active.keys.toList().forEach { close(it, end) }
        return intervals.mapValues { (_, spans) ->
            var total = 0L
            var left = -1L
            var right = -1L
            spans.sortedBy { it.first }.forEach { (a, b) ->
                if (a > right) { if (left >= 0) total += right - left; left = a; right = b }
                else right = maxOf(right, b)
            }
            total + if (left >= 0) right - left else 0L
        }
    }
}
