package com.dk.appblocker

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.time.LocalDateTime

object RuleEngine {
    data class Match(val blocked: Boolean, val reason: String = "", val plan: BlockPlan? = null)

    fun shouldBlock(context: Context, packageName: String): Match {
        if (packageName == context.packageName) return Match(false)

        val now = System.currentTimeMillis()
        if (Prefs.packageAllowedUntil(context, packageName) > now) return Match(false)

        val focusEnd = Prefs.focusEnd(context)
        if (focusEnd > now && packageName in Prefs.focusPackages(context)) {
            return Match(true, "Focus session", null)
        }

        val plans = Prefs.getPlans(context)
        for (plan in plans) {
            if (!plan.enabled || packageName !in plan.packages) continue
            if (isPlanActive(context, plan)) return Match(true, reasonFor(plan), plan)
        }
        return Match(false)
    }

    fun isPlanActive(context: Context, plan: BlockPlan): Boolean = when (plan.triggerType) {
        TriggerType.MANUAL -> plan.manualActive
        TriggerType.LOCATION -> plan.id in Prefs.getActiveGeofences(context)
        TriggerType.DAILY_LIMIT -> {
            if (!hasUsageAccess(context)) false
            else usageTodayMillis(context, plan.packages) >= plan.dailyLimitMinutes * 60_000L
        }
        TriggerType.SCHEDULE -> isScheduleActive(plan)
    }

    private fun isScheduleActive(plan: BlockPlan): Boolean {
        val now = LocalDateTime.now()
        val day = now.dayOfWeek.value
        val minute = now.hour * 60 + now.minute
        if (plan.startMinute == plan.endMinute) return day in plan.days
        return if (plan.startMinute < plan.endMinute) {
            day in plan.days && minute in plan.startMinute until plan.endMinute
        } else {
            val previousDay = if (day == 1) 7 else day - 1
            (day in plan.days && minute >= plan.startMinute) ||
                (previousDay in plan.days && minute < plan.endMinute)
        }
    }

    private fun reasonFor(plan: BlockPlan): String = when (plan.triggerType) {
        TriggerType.SCHEDULE -> "${plan.name} · scheduled block"
        TriggerType.DAILY_LIMIT -> "${plan.name} · ${plan.dailyLimitMinutes}m daily limit reached"
        TriggerType.LOCATION -> "${plan.name} · location block"
        TriggerType.MANUAL -> "${plan.name} · focus block"
    }

    fun usageTodayMillis(context: Context, packages: Collection<String>): Long {
        if (packages.isEmpty()) return 0L
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = startOfTodayMillis()
        val end = System.currentTimeMillis()
        val events = manager.queryEvents(start, end)
        val event = UsageEvents.Event()
        val foregroundStarts = mutableMapOf<String, Long>()
        val totals = mutableMapOf<String, Long>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (pkg !in packages) continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> foregroundStarts[pkg] = event.timeStamp

                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val started = foregroundStarts.remove(pkg)
                    if (started != null && event.timeStamp >= started) {
                        totals[pkg] = (totals[pkg] ?: 0L) + (event.timeStamp - started)
                    }
                }
            }
        }

        val now = System.currentTimeMillis()
        foregroundStarts.forEach { (pkg, started) -> totals[pkg] = (totals[pkg] ?: 0L) + (now - started).coerceAtLeast(0) }
        return totals.values.sum()
    }

    fun topUsageToday(context: Context): List<UsageRow> = topUsage(context, startOfTodayMillis(), System.currentTimeMillis())

    fun topUsage(context: Context, start: Long, end: Long): List<UsageRow> {
        if (!hasUsageAccess(context)) return emptyList()
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end) ?: return emptyList()
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
