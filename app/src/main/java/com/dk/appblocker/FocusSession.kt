package com.dk.appblocker

import java.util.Locale
import java.util.UUID

/** One immutable blocklist and monotonic deadline, written as a single preferences value. */
data class FocusSession(
    val id: String,
    val packages: Set<String>,
    val durationMillis: Long,
    val startedElapsed: Long,
    val deadline: Deadline
) {
    fun remaining(elapsedNow: Long, bootNow: Int): Long =
        deadline.remaining(elapsedNow, bootNow).coerceAtMost(durationMillis)

    fun progress(elapsedNow: Long, bootNow: Int): Float =
        (1.0 - remaining(elapsedNow, bootNow).toDouble() / durationMillis).toFloat().coerceIn(0f, 1f)

    companion object {
        fun create(packages: Set<String>, duration: Long, wallNow: Long, elapsedNow: Long, boot: Int): FocusSession? =
            runCatching {
                require(duration > 0 && packages.isNotEmpty() && packages.all { it.isNotBlank() })
                require(wallNow >= 0 && elapsedNow >= 0)
                FocusSession(UUID.randomUUID().toString(), packages.toSet(), duration, elapsedNow,
                    Deadline(Math.addExact(wallNow, duration), Math.addExact(elapsedNow, duration), boot))
            }.getOrNull()
    }
}

enum class FocusStartResult { STARTED, ALREADY_ACTIVE, INVALID_DURATION, NO_APPS, LOCKED, STORAGE_ERROR }

/** Empty fields mean zero; minutes may exceed 59 and are normalised rather than clamped. */
fun focusDurationMillis(hours: String, minutes: String): Long? = runCatching {
    fun number(text: String): Long {
        val value = text.trim()
        require(value.isEmpty() || value.all { it in '0'..'9' })
        return if (value.isEmpty()) 0 else value.toLong()
    }
    val totalMinutes = Math.addExact(Math.multiplyExact(number(hours), 60L), number(minutes))
    Math.multiplyExact(totalMinutes, 60_000L).also { require(it > 0L) }
}.getOrNull()

fun formatFocusCountdown(millis: Long): String {
    val bounded = millis.coerceAtLeast(0L)
    val seconds = bounded / 1000 + if (bounded % 1000 == 0L) 0 else 1
    val days = seconds / 86_400
    val clock = String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600 % 24, seconds / 60 % 60, seconds % 60)
    return if (days > 0) "${days}d $clock" else clock
}
