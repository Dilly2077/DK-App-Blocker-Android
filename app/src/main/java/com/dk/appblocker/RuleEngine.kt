package com.dk.appblocker

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.time.LocalDateTime

object RuleEngine {
    data class Match(val blocked: Boolean, val reason: String = "", val plan: BlockPlan? = null,
        val strict: Boolean = false, val allowBreaks: Boolean = true, val policyToken: String = "")

    fun shouldBlock(context: Context, packageName: String): Match {
        if (isRecoveryPackage(context, packageName)) return Match(false)

        val now = System.currentTimeMillis()
        val focusEnd = Prefs.focusEnd(context)
        val focus = focusEnd > now && packageName in Prefs.focusPackages(context)
        val plans = Prefs.getPlans(context).filter { it.enabled && packageName in it.packages && isPlanActive(context, it) }
        val match = BlockingPolicy.combine(plans, focus)
        val strict = Prefs.getStrict(context)
        val token = com.google.gson.Gson().toJson(plans.sortedBy { it.id }) +
            (if (focus) Prefs.focusToken(context) else "") + "|${strict.enabled}|${strict.unlockMethod}"
        if (match.allowBreaks && token == Prefs.allowedPolicy(context, packageName) && Prefs.packageAllowedUntil(context, packageName) > now) return Match(false)
        return match.copy(policyToken = token, strict = match.strict || strict.enabled)
    }

    fun isPlanActive(context: Context, plan: BlockPlan): Boolean = when (plan.triggerType) {
        TriggerType.MANUAL -> plan.manualActive
        TriggerType.LOCATION -> GeofenceManager.hasPermissions(context) && plan.id in Prefs.getActiveGeofences(context)
        TriggerType.DAILY_LIMIT -> {
            if (!hasUsageAccess(context)) false
            else BlockingPolicy.dailyLimitReached(usageTodayMillis(context, plan.packages), plan.dailyLimitMinutes)
        }
        TriggerType.SCHEDULE -> BlockingPolicy.scheduleActive(plan, LocalDateTime.now())
    }

    fun usageTodayMillis(context: Context, packages: Collection<String>): Long {
        if (packages.isEmpty() || !hasUsageAccess(context)) return 0L
        return usageTotals(context, startOfTodayMillis(), System.currentTimeMillis()).filterKeys { it in packages }.values.sum()
    }

    private fun usageTotals(context: Context, start: Long, end: Long): Map<String, Long> = runCatching {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        // Include yesterday to seed sessions spanning midnight. Android retains only a few days of events.
        val events = manager.queryEvents((start - 24 * 60 * 60_000L).coerceAtLeast(0), end)
        val event = UsageEvents.Event()
        val transitions = mutableListOf<UsageAccumulator.Transition>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND, UsageEvents.Event.MOVE_TO_BACKGROUND ->
                    transitions += UsageAccumulator.Transition(event.packageName.orEmpty(), event.className.orEmpty(), event.timeStamp, event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN ->
                    transitions += UsageAccumulator.Transition("", "", event.timeStamp, false)
            }
        }
        UsageAccumulator.totals(transitions, start, end)
    }.getOrDefault(emptyMap())

    fun topUsageToday(context: Context): List<UsageRow> = topUsage(context, startOfTodayMillis(), System.currentTimeMillis())

    fun topUsage(context: Context, start: Long, end: Long): List<UsageRow> {
        if (!hasUsageAccess(context)) return emptyList()
        // Aggregated stats are the OS's historical source; daily enforcement uses clipped events above.
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats = runCatching { manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end) }.getOrNull() ?: return emptyList()
        val pm = context.packageManager
        return stats
            .filter { it.totalTimeInForeground > 0 && it.packageName != context.packageName }
            .groupBy { it.packageName }
            .map { (packageName, rows) ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                }.getOrDefault(packageName)
                UsageRow(label, packageName, rows.sumOf { it.totalTimeInForeground })
            }
            .sortedByDescending { it.millis }
    }
}
