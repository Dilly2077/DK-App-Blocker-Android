package com.dk.appblocker

enum class TriggerType {
    SCHEDULE,
    DAILY_LIMIT,
    LOCATION,
    MANUAL
}

data class BlockPlan(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "New rule",
    val triggerType: TriggerType = TriggerType.SCHEDULE,
    val packages: List<String> = emptyList(),
    val enabled: Boolean = true,
    val strict: Boolean = false,
    val allowBreaks: Boolean = true,
    val days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 17 * 60,
    val dailyLimitMinutes: Int = 30,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Float = 150f,
    val manualActive: Boolean = false
)

data class StrictSettings(
    val enabled: Boolean = false,
    val pinHash: String = "",
    val editCooldownMinutes: Int = 0,
    val blockRuleChanges: Boolean = true,
    val preventUninstall: Boolean = true,
    val blockDeviceSettings: Boolean = true,
    val blockRecents: Boolean = true,
    val blockSplitScreen: Boolean = true
)

data class BlockEvent(
    val timestamp: Long,
    val packageName: String,
    val reason: String
)

data class InstalledApp(
    val label: String,
    val packageName: String
)

data class UsageRow(
    val label: String,
    val packageName: String,
    val millis: Long
)
