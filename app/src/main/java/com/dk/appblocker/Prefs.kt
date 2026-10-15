package com.dk.appblocker

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest
import android.os.SystemClock
import android.provider.Settings

@android.annotation.SuppressLint("ApplySharedPref") // Credentials and deadlines must be durable before returning success.
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
            (gson.fromJson<MutableList<BlockPlan>>(json, type) ?: mutableListOf())
                .filter { plan ->
                    plan.id.isNotBlank() && plan.name.isNotBlank() && plan.triggerType in TriggerType.entries &&
                        plan.packages.all { it.isNotBlank() } && plan.days.all { it in 1..7 } &&
                        plan.startMinute in 0..1439 && plan.endMinute in 0..1439 &&
                        plan.dailyLimitMinutes in 1..1440 && plan.latitude.isFinite() && plan.longitude.isFinite() && plan.radiusMeters.isFinite()
                }.distinctBy { it.id }.toMutableList()
        }.getOrDefault(mutableListOf())
    }

    fun savePlans(context: Context, plans: List<BlockPlan>): Boolean =
        p(context).edit().putString(KEY_PLANS, gson.toJson(plans)).commit()

    fun getStrict(context: Context): StrictSettings {
        val json = p(context).getString(KEY_STRICT, null) ?: return StrictSettings()
        val parsed = runCatching { gson.fromJson(json, StrictSettings::class.java) ?: StrictSettings() }
            .getOrDefault(StrictSettings())
        var normalized = parsed.copy(
            pinHash = parsed.pinHash ?: "",
            editCooldownMinutes = parsed.editCooldownMinutes.coerceIn(0, 60),
            blockRuleChanges = if (json.contains("blockRuleChanges")) parsed.blockRuleChanges else true,
            preventUninstall = if (json.contains("preventUninstall")) parsed.preventUninstall else true,
            blockDeviceSettings = if (json.contains("blockDeviceSettings")) parsed.blockDeviceSettings else true,
            blockRecents = if (json.contains("blockRecents")) parsed.blockRecents else true,
            blockSplitScreen = if (json.contains("blockSplitScreen")) parsed.blockSplitScreen else true,
            unlockMethod = parsed.unlockMethod?.takeIf { it in setOf("TIMER", "PASSWORD", "BIOMETRIC") } ?: "PASSWORD",
            timerDurationMinutes = parsed.timerDurationMinutes.takeIf { it in 5..1440 } ?: 60
        )
        if (normalized.enabled && normalized.unlockMethod == "PASSWORD" && normalized.pinHash.isBlank()) {
            normalized = normalized.copy(enabled = false)
            saveStrict(context, normalized)
        }
        if (normalized.enabled && normalized.unlockMethod == "TIMER") {
            if (normalized.lockBoot == -1) {
                val remaining = (normalized.lockUntil - System.currentTimeMillis()).coerceIn(0L, normalized.timerDurationMinutes * 60_000L)
                normalized = normalized.copy(lockElapsedEnd = SystemClock.elapsedRealtime() + remaining, lockBoot = boot(context))
                saveStrict(context, normalized)
            }
            if (Deadline(normalized.lockUntil, normalized.lockElapsedEnd, normalized.lockBoot).remaining(SystemClock.elapsedRealtime(), boot(context)) == 0L) {
                normalized = normalized.copy(enabled = false, lockUntil = 0L, lockElapsedEnd = 0L)
                saveStrict(context, normalized)
            }
        }
        return normalized
    }

    fun saveStrict(context: Context, settings: StrictSettings) {
        p(context).edit().putString(KEY_STRICT, gson.toJson(settings)).commit()
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

    fun saveBlockScreen(context: Context, settings: BlockScreenSettings): Boolean =
        p(context).edit().putString(KEY_BLOCK_SCREEN, gson.toJson(settings)).commit()

    fun hashPin(pin: String): String = CredentialHasher.hash(pin)

    @Synchronized
    fun checkPin(context: Context, pin: String): Boolean {
        if (remaining(context, "auth_retry") > 0) return false
        val stored = getStrict(context).pinHash
        val valid = stored.isNotBlank() && CredentialHasher.verify(pin, stored)
        if (valid) {
            p(context).edit().remove("auth_failures").remove("auth_retry_deadline").commit()
            if (CredentialHasher.needsMigration(stored)) saveStrict(context, getStrict(context).copy(pinHash = hashPin(pin)))
        } else {
            val failures = p(context).getInt("auth_failures", 0) + 1
            p(context).edit().putInt("auth_failures", if (failures >= 5) 0 else failures).commit()
            if (failures >= 5) putDeadline(context, "auth_retry", 30_000L)
        }
        return valid
    }

    @Synchronized
    fun replacePin(context: Context, old: String, replacement: String): Boolean {
        if (replacement.length !in 4..64) return false
        if (getStrict(context).pinHash.isNotBlank() && !checkPin(context, old)) return false
        saveStrict(context, getStrict(context).copy(pinHash = hashPin(replacement)))
        return true
    }

    fun boot(context: Context): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    private fun deadlineJson(context: Context, duration: Long): String {
        val bounded = duration.coerceIn(0L, 24 * 60 * 60_000L)
        val deadline = Deadline(System.currentTimeMillis() + bounded, SystemClock.elapsedRealtime() + bounded, boot(context))
        return gson.toJson(deadline)
    }
    private fun putDeadline(context: Context, key: String, duration: Long) {
        p(context).edit().putString(key + "_deadline", deadlineJson(context, duration)).commit()
    }
    private fun remaining(context: Context, key: String): Long = runCatching {
        gson.fromJson(p(context).getString(key + "_deadline", null), Deadline::class.java)
            ?.remaining(SystemClock.elapsedRealtime(), boot(context)) ?: 0L
    }.getOrDefault(0L)
    fun strictRemaining(context: Context): Long = getStrict(context).let {
        Deadline(it.lockUntil, it.lockElapsedEnd, it.lockBoot).remaining(SystemClock.elapsedRealtime(), boot(context))
    }
    fun canEditRules(context: Context): Boolean = getStrict(context).let { !it.enabled || !it.blockRuleChanges } && editLockedUntil(context) <= System.currentTimeMillis()

    @Synchronized
    fun setActiveGeofence(context: Context, id: String, active: Boolean) {
        val current = getActiveGeofences(context).toMutableSet()
        if (active) current += id else current -= id
        p(context).edit().putStringSet(KEY_GEOFENCES, current).commit()
    }

    fun getActiveGeofences(context: Context): Set<String> =
        p(context).getStringSet(KEY_GEOFENCES, emptySet())?.toSet() ?: emptySet()

    fun clearGeofences(context: Context) { p(context).edit().remove(KEY_GEOFENCES).commit() }
    fun geofenceStatus(context: Context): String = p(context).getString("geofence_status", "Not registered") ?: "Not registered"
    fun setGeofenceStatus(context: Context, status: String) { p(context).edit().putString("geofence_status", status).apply() }
    fun geofenceFingerprint(context: Context): String? = p(context).getString("geofence_fingerprint", null)
    fun setGeofenceFingerprint(context: Context, fingerprint: String?) { p(context).edit().putString("geofence_fingerprint", fingerprint).apply() }

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
        val duration = durationMinutes.coerceIn(1, 1440) * 60_000L
        p(context).edit().putString("focus_deadline", deadlineJson(context, duration))
            .putLong(KEY_FOCUS_END, System.currentTimeMillis() + duration).putStringSet(KEY_FOCUS_PACKAGES, packages.toSet()).commit()
    }

    fun stopFocus(context: Context) {
        p(context).edit().remove(KEY_FOCUS_END).remove(KEY_FOCUS_PACKAGES).remove("focus_deadline").commit()
    }

    fun focusEnd(context: Context): Long = remaining(context, "focus").let { if (it > 0) System.currentTimeMillis() + it else 0L }
    fun focusToken(context: Context): String = p(context).getString("focus_deadline", "").orEmpty()
    fun focusPackages(context: Context): Set<String> = p(context).getStringSet(KEY_FOCUS_PACKAGES, emptySet())?.toSet() ?: emptySet()

    fun allowPackageUntil(context: Context, pkg: String, until: Long, policyToken: String = "") {
        p(context).edit().putString(KEY_ALLOW_UNTIL_PREFIX + pkg + "_deadline", deadlineJson(context, until - System.currentTimeMillis()))
            .putString("allow_policy_$pkg", policyToken).commit()
    }

    fun packageAllowedUntil(context: Context, pkg: String): Long = remaining(context, KEY_ALLOW_UNTIL_PREFIX + pkg).let { if (it > 0) System.currentTimeMillis() + it else 0L }
    fun allowedPolicy(context: Context, pkg: String): String = p(context).getString("allow_policy_$pkg", "").orEmpty()

    fun setEditLockedUntil(context: Context, until: Long) {
        putDeadline(context, KEY_EDIT_LOCKED_UNTIL, until - System.currentTimeMillis())
    }

    fun editLockedUntil(context: Context): Long = remaining(context, KEY_EDIT_LOCKED_UNTIL).let { if (it > 0) System.currentTimeMillis() + it else 0L }

    fun exportJson(context: Context): String {
        val payload = mapOf(
            "version" to 3,
            "plans" to getPlans(context),
            "strict" to getStrict(context).copy(pinHash = "", enabled = false, lockUntil = 0L, lockElapsedEnd = 0L),
            "blockScreen" to getBlockScreen(context)
        )
        return gson.toJson(payload)
    }
}
