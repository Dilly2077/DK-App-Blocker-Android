package com.dk.appblocker

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest

object Prefs {
    private const val NAME = "dk_blocker_prefs"
    private const val KEY_PLANS = "plans"
    private const val KEY_STRICT = "strict"
    private const val KEY_BLOCK_SCREEN = "block_screen"
    private const val KEY_GEOFENCES = "active_geofences"
    private const val KEY_EVENTS = "block_events"
    private const val KEY_FOCUS_END = "focus_end"
    private const val KEY_FOCUS_PACKAGES = "focus_packages"
    private const val KEY_ALLOW_UNTIL_PREFIX = "allow_until_"
    private const val KEY_EDIT_LOCKED_UNTIL = "edit_locked_until"
    private val gson = Gson()

    private fun p(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getPlans(context: Context): MutableList<BlockPlan> {
        val json = p(context).getString(KEY_PLANS, null) ?: return mutableListOf()
        return runCatching {
            val type = object : TypeToken<MutableList<BlockPlan>>() {}.type
            gson.fromJson<MutableList<BlockPlan>>(json, type) ?: mutableListOf()
        }.getOrDefault(mutableListOf())
    }

    fun savePlans(context: Context, plans: List<BlockPlan>) {
        p(context).edit().putString(KEY_PLANS, gson.toJson(plans)).apply()
    }

    fun getStrict(context: Context): StrictSettings {
        val json = p(context).getString(KEY_STRICT, null) ?: return StrictSettings()
        val parsed = runCatching { gson.fromJson(json, StrictSettings::class.java) ?: StrictSettings() }
            .getOrDefault(StrictSettings())
        var normalized = parsed.copy(
            blockRuleChanges = if (json.contains("blockRuleChanges")) parsed.blockRuleChanges else true,
            preventUninstall = if (json.contains("preventUninstall")) parsed.preventUninstall else true,
            blockDeviceSettings = if (json.contains("blockDeviceSettings")) parsed.blockDeviceSettings else true,
            blockRecents = if (json.contains("blockRecents")) parsed.blockRecents else true,
            blockSplitScreen = if (json.contains("blockSplitScreen")) parsed.blockSplitScreen else true,
            unlockMethod = parsed.unlockMethod?.takeIf { it in setOf("TIMER", "PASSWORD", "BIOMETRIC") } ?: "PASSWORD",
            timerDurationMinutes = parsed.timerDurationMinutes.takeIf { it in 5..1440 } ?: 60
        )
        if (normalized.enabled && normalized.unlockMethod == "TIMER" && normalized.lockUntil in 1..System.currentTimeMillis()) {
            normalized = normalized.copy(enabled = false, lockUntil = 0L)
            saveStrict(context, normalized)
        }
        return normalized
    }

    fun saveStrict(context: Context, settings: StrictSettings) {
        p(context).edit().putString(KEY_STRICT, gson.toJson(settings)).apply()
    }

    fun getBlockScreen(context: Context): BlockScreenSettings {
        val json = p(context).getString(KEY_BLOCK_SCREEN, null) ?: return BlockScreenSettings()
        return runCatching {
            val parsed = gson.fromJson(json, BlockScreenSettings::class.java) ?: BlockScreenSettings()
            parsed.copy(
                title = parsed.title.ifBlank { "Blocked" },
                message = parsed.message.ifBlank { "Stay focused. {app} is blocked right now." },
                backgroundHex = parsed.backgroundHex.ifBlank { "#020604" },
                accentHex = parsed.accentHex.ifBlank { "#35F47A" }
            )
        }.getOrDefault(BlockScreenSettings())
    }

    fun saveBlockScreen(context: Context, settings: BlockScreenSettings) {
        p(context).edit().putString(KEY_BLOCK_SCREEN, gson.toJson(settings)).apply()
    }

    fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun checkPin(context: Context, pin: String): Boolean {
        val stored = getStrict(context).pinHash
        return stored.isNotBlank() && stored == hashPin(pin)
    }

    fun setActiveGeofence(context: Context, id: String, active: Boolean) {
        val current = getActiveGeofences(context).toMutableSet()
        if (active) current += id else current -= id
        p(context).edit().putStringSet(KEY_GEOFENCES, current).apply()
    }

    fun getActiveGeofences(context: Context): Set<String> =
        p(context).getStringSet(KEY_GEOFENCES, emptySet())?.toSet() ?: emptySet()

    fun addBlockEvent(context: Context, event: BlockEvent) {
        val events = getBlockEvents(context).toMutableList()
        events += event
        val cutoff = System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000
        val trimmed = events.filter { it.timestamp >= cutoff }.takeLast(2000)
        p(context).edit().putString(KEY_EVENTS, gson.toJson(trimmed)).apply()
    }

    fun getBlockEvents(context: Context): List<BlockEvent> {
        val json = p(context).getString(KEY_EVENTS, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<BlockEvent>>() {}.type
            gson.fromJson<List<BlockEvent>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun startFocus(context: Context, packages: Set<String>, durationMinutes: Int) {
        val end = System.currentTimeMillis() + durationMinutes * 60_000L
        p(context).edit().putLong(KEY_FOCUS_END, end).putStringSet(KEY_FOCUS_PACKAGES, packages).apply()
    }

    fun stopFocus(context: Context) {
        p(context).edit().remove(KEY_FOCUS_END).remove(KEY_FOCUS_PACKAGES).apply()
    }

    fun focusEnd(context: Context): Long = p(context).getLong(KEY_FOCUS_END, 0L)
    fun focusPackages(context: Context): Set<String> = p(context).getStringSet(KEY_FOCUS_PACKAGES, emptySet())?.toSet() ?: emptySet()

    fun allowPackageUntil(context: Context, pkg: String, until: Long) {
        p(context).edit().putLong(KEY_ALLOW_UNTIL_PREFIX + pkg, until).apply()
    }

    fun packageAllowedUntil(context: Context, pkg: String): Long = p(context).getLong(KEY_ALLOW_UNTIL_PREFIX + pkg, 0L)

    fun setEditLockedUntil(context: Context, until: Long) {
        p(context).edit().putLong(KEY_EDIT_LOCKED_UNTIL, until).apply()
    }

    fun editLockedUntil(context: Context): Long = p(context).getLong(KEY_EDIT_LOCKED_UNTIL, 0L)

    fun exportJson(context: Context): String {
        val payload = mapOf(
            "version" to 3,
            "plans" to getPlans(context),
            "strict" to getStrict(context),
            "blockScreen" to getBlockScreen(context)
        )
        return gson.toJson(payload)
    }
}
