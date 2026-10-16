package com.dk.appblocker

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberRuleStatuses(plans: List<BlockPlan>, tick: Long, permissions: BlockingPermissions): Map<String, RuleStatus> {
    val context = LocalContext.current
    val triggers by produceState<Map<String, Boolean>>(emptyMap(), plans, tick) {
        value = withContext(Dispatchers.Default) { plans.associate { it.id to (it.enabled && RuleEngine.isPlanActive(context, it)) } }
    }
    val locationReady = remember(tick) { GeofenceManager.hasPermissions(context) && Prefs.geofenceFingerprint(context) != null }
    return plans.associate { it.id to ruleStatus(it, triggers[it.id] == true, permissions.operational, permissions.usage, locationReady) }
}

@Composable
internal fun HomeScreen(plans: List<BlockPlan>, tick: Long, onCreateRule: () -> Unit, onEdit: (BlockPlan) -> Unit,
                        onStrict: () -> Unit, onSeeAll: () -> Unit) {
    val permissions = rememberBlockingPermissions(tick)
    val statuses = rememberRuleStatuses(plans, tick, permissions)
    val relevant = plans.sortedWith(compareByDescending<BlockPlan> { statuses[it.id]?.triggerActive == true }.thenByDescending { it.enabled }).take(3)
    LazyColumn(Modifier.fillMaxSize().testTag("homeList"),
        contentPadding = PaddingValues(DKSpace.screen), verticalArrangement = Arrangement.spacedBy(DKSpace.gap)) {
        item(key = "greeting") {
            Text("DK / FOCUS", color = Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Your focus, first.", style = MaterialTheme.typography.headlineLarge)
            Text("A little space for what matters.", color = Muted, fontSize = 13.sp)
        }
        item(key = "status") { BlockingSetupStatus(permissions, plans.any { it.enabled && it.triggerType == TriggerType.DAILY_LIMIT }) }
        item(key = "focus") { QuickFocusCard(tick, permissions.operational, onStrict) }
        item(key = "rules-title") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Your rules", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onSeeAll, modifier = Modifier.testTag("seeAllRules")) { Text("See all") }
            }
        }
        if (plans.isEmpty()) item(key = "empty") { EmptyRuleCard(onCreateRule) }
        else items(relevant, key = { it.id }) { plan -> HomeRuleRow(plan, statuses.getValue(plan.id)) { onEdit(plan) } }
        item(key = "create") { OutlinedButton(onClick = onCreateRule, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Text("Create a rule") } }
    }
}

@Composable
internal fun BlockingSetupStatus(permissions: BlockingPermissions, showUsage: Boolean = true) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(DKSpace.compact)) {
        Surface(Modifier.fillMaxWidth().then(if (!permissions.accessibility) Modifier.testTag("blockingSetupBanner") else Modifier),
            shape = RoundedCornerShape(16.dp), color = if (permissions.operational) Green.copy(alpha = .08f) else Panel2,
            border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
            Column(Modifier.padding(DKSpace.card)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(if (permissions.operational) Green else Amber, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(when { permissions.operational -> "Blocking enabled"; !permissions.accessibility -> "Blocking needs setup"; else -> "Blocking unavailable" },
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                }
                if (!permissions.operational) {
                    Text(if (permissions.accessibility) "Accessibility is enabled; the service is not connected." else "Enable Accessibility to enforce app blocks.", color = Muted, fontSize = 12.sp)
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                        Text(if (permissions.accessibility) "Review Accessibility" else "Enable blocking")
                    }
                }
            }
        }
        if (showUsage && !permissions.usage) {
            Surface(Modifier.fillMaxWidth().testTag("usageSetupBanner"), shape = RoundedCornerShape(16.dp), color = Panel2) {
                Column(Modifier.padding(DKSpace.card)) {
                    Text("Daily limits need Usage Access", fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Set up daily limits") }
                }
            }
        }
    }
}

@Composable
internal fun EmptyRuleCard(onCreateRule: () -> Unit) {
    GlowCard(onCreateRule) {
        Row(Modifier.padding(DKSpace.card), verticalAlignment = Alignment.CenterVertically) {
            Text("🌱", fontSize = 28.sp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Build a calmer routine", fontWeight = FontWeight.Bold)
                Text("Try a schedule, daily limit or location rule.", color = Muted, fontSize = 12.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

@Composable
internal fun HomeRuleRow(plan: BlockPlan, status: RuleStatus, onClick: () -> Unit) {
    GlowCard(onClick) {
        Row(Modifier.fillMaxWidth().padding(DKSpace.card), verticalAlignment = Alignment.CenterVertically) {
            Text(ruleEmoji(plan), fontSize = 24.sp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(plan.name, fontWeight = FontWeight.SemiBold)
                Text(ruleSummary(plan), color = Muted, fontSize = 12.sp)
                Text(status.label, color = if (status.triggerActive && status.operational) Green else Muted, fontSize = 12.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}
