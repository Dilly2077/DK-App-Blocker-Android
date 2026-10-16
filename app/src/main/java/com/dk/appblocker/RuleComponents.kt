package com.dk.appblocker

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlin.math.roundToInt
internal fun ruleEmoji(plan: BlockPlan): String = when (plan.triggerType) {
    TriggerType.DAILY_LIMIT -> "⏰"
    TriggerType.LOCATION -> "📍"
    TriggerType.MANUAL -> "🍃"
    TriggerType.SCHEDULE -> when {
        plan.name.contains("Morning", true) -> "☀️"
        plan.name.contains("Wind", true) || plan.startMinute >= 18 * 60 -> "🌙"
        plan.name.contains("Campus", true) -> "🏫"
        else -> "🗓️"
    }
}

internal fun ruleSummary(plan: BlockPlan): String = when (plan.triggerType) {
    TriggerType.DAILY_LIMIT -> "${plan.dailyLimitMinutes}m daily • ${appCount(plan.packages.size)}"
    TriggerType.SCHEDULE -> "${formatMinute(plan.startMinute)} – ${formatMinute(plan.endMinute)} • ${daysLabel(plan.days)}"
    TriggerType.LOCATION -> "Within ${plan.radiusMeters.roundToInt()}m • ${appCount(plan.packages.size)}"
    TriggerType.MANUAL -> if (plan.manualActive) "Manual focus is on" else "Manual focus is off"
}

internal fun appCount(count: Int): String = "$count ${if (count == 1) "app" else "apps"}"

internal fun daysLabel(days: Set<Int>): String = when {
    days.size == 7 -> "Every day"
    days == setOf(1, 2, 3, 4, 5) -> "Mon–Fri"
    days.isEmpty() -> "No days"
    else -> days.sorted().joinToString(" ") { listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[it - 1] }
}

internal fun formatMinute(minute: Int): String = "%02d:%02d".format((minute / 60) % 24, minute % 60)
