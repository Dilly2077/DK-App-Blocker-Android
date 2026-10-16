package com.dk.appblocker

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class UsageSnapshot(val period: InsightPeriod, val rows: List<UsageRow>, val loaded: Boolean)

@Composable
internal fun InsightsScreen(tick: Long) {
    val context = LocalContext.current
    var period by rememberSaveable { mutableStateOf(InsightPeriod.DAY) }
    val hasAccess = rememberBlockingPermissions(tick).usage
    val snapshot by produceState(UsageSnapshot(period, emptyList(), false), period, tick / 30_000L, hasAccess) {
        val start = LocalDate.now().minusDays(period.days - 1L).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        value = UsageSnapshot(period, withContext(Dispatchers.IO) { if (hasAccess) RuleEngine.topUsage(context, start, tick) else emptyList() }, true)
    }
    val events by produceState(emptyList<BlockEvent>(), tick / 5000L) { value = withContext(Dispatchers.IO) { Prefs.getBlockEvents(context) } }
    val usage = snapshot.takeIf { it.period == period }?.rows.orEmpty()
    val buckets = remember(period, events, tick / 60_000L) { attemptBuckets(period, events) }
    val total = usage.sumOf { it.millis }
    LazyColumn(Modifier.fillMaxSize().testTag("insightsList"), contentPadding = PaddingValues(DKSpace.screen),
        verticalArrangement = Arrangement.spacedBy(DKSpace.gap)) {
        item { Text("Insights", style = MaterialTheme.typography.headlineLarge); Text(period.heading, color = Muted, modifier = Modifier.testTag("insightPeriodHeading")) }
        item { Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(999.dp)).padding(4.dp)) {
            InsightPeriod.entries.forEach { option -> PeriodPill(option.label, period == option, Modifier.weight(1f)) { period = option } }
        } }
        if (!hasAccess) item { PermissionCard("Screen-time access required", "Usage Access is optional for focus and schedules. Enable it to measure app use.", "Grant access") {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } }
        else {
            item { LabeledCard("Screen time", "Android-reported totals · OS aggregation may affect boundaries") {
                if (snapshot.period != period || !snapshot.loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
                else {
                    Text(formatDuration(total), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Green)
                    Text("${usage.size} apps with recorded usage", color = Muted, fontSize = 12.sp)
                }
            } }
            item { Text("Most used apps", style = MaterialTheme.typography.titleLarge) }
            if (snapshot.loaded && usage.isEmpty()) item { Text("No usage reported for this period. Android may take time to provide it.", color = Muted) }
            else items(usage.take(10), key = { it.packageName }) { UsageRowCard(it, total) }
        }
        item { LabeledCard("Recorded blocking attempts", "${buckets.sumOf { it.count }} attempts · ${period.heading.lowercase()}") {
            if (buckets.all { it.count == 0L }) Text("No blocking attempts recorded for this period.", color = Muted, fontSize = 13.sp)
            BlockAttemptBars(buckets)
            Text("Observed by this app. Repeated launches within 30 seconds count once; this is not a count of every blocked interaction.", color = Muted, fontSize = 11.sp)
        } }
    }
}

@Composable
internal fun PeriodPill(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FilterChip(selected, onClick, label = { Text(text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        modifier = modifier.padding(horizontal = 2.dp))
}

@Composable
internal fun BlockAttemptBars(attempts: List<AttemptBucket>) {
    val maximum = (attempts.maxOfOrNull { it.count } ?: 0L).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        attempts.forEach { bucket ->
            Column(Modifier.weight(1f).clearAndSetSemantics { contentDescription = "${bucket.description}: ${bucket.count} recorded attempts" },
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(bucket.count.toString(), color = Green, fontSize = 11.sp)
                Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                    val height = size.height * bucket.count.toFloat() / maximum
                    val width = size.width.coerceAtMost(18.dp.toPx())
                    drawRoundRect(Green.copy(alpha = .85f), Offset((size.width - width) / 2, size.height - height), Size(width, height), CornerRadius(6.dp.toPx()))
                }
                Text(bucket.label, color = Muted, fontSize = 10.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
internal fun UsageRowCard(row: UsageRow, total: Long) {
    val fraction = if (total > 0) row.millis.toFloat() / total else 0f
    GlowCard {
        Row(Modifier.padding(DKSpace.card), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(row.packageName)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(row.label, fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    color = Green, trackColor = Line)
            }
            Text(formatDuration(row.millis), fontSize = 13.sp)
        }
    }
}
