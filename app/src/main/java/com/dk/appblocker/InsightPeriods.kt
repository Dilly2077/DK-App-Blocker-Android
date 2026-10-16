package com.dk.appblocker

import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class InsightPeriod(val label: String, val days: Int, val heading: String) {
    DAY("Day", 1, "Today"), WEEK("Week", 7, "Last 7 days"), MONTH("Month", 30, "Last 30 days")
}
data class AttemptBucket(val label: String, val description: String, val count: Long)

fun attemptBuckets(period: InsightPeriod, events: List<BlockEvent>, today: LocalDate = LocalDate.now(),
                   zone: ZoneId = ZoneId.systemDefault()): List<AttemptBucket> {
    val short = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    val date = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    return when (period) {
        InsightPeriod.DAY -> (0..5).map { index ->
            val count = events.count {
                val at = Instant.ofEpochMilli(it.timestamp).atZone(zone)
                at.toLocalDate() == today && at.hour / 4 == index
            }.toLong()
            AttemptBucket("%02d".format(index * 4), "${index * 4}:00–${index * 4 + 4}:00", count)
        }
        InsightPeriod.WEEK -> (6 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            AttemptBucket(short.format(day), day.toString(), events.count { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() == day }.toLong())
        }
        InsightPeriod.MONTH -> (0..4).map { index ->
            val from = today.minusDays(29).plusDays(index * 6L)
            val until = from.plusDays(6)
            AttemptBucket(date.format(from), "${date.format(from)}–${date.format(until.minusDays(1))}",
                events.count {
                    val day = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
                    !day.isBefore(from) && day.isBefore(until)
                }.toLong())
        }
    }
}
