package com.dk.appblocker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun RulesScreen(plans: List<BlockPlan>, tick: Long, busy: Boolean, onCreateRule: () -> Unit,
                         onEdit: (BlockPlan) -> Unit, onAction: (BlockPlan, RuleAction) -> Unit) {
    val permissions = rememberBlockingPermissions(tick)
    val statuses = rememberRuleStatuses(plans, tick, permissions)
    var filter by rememberSaveable { mutableStateOf(RuleFilter.ALL) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val visible = plans.filter { filter.includes(it, statuses.getValue(it.id)) }
    val deleting = plans.firstOrNull { it.id == deleteId }
    if (deleting != null) AlertDialog(onDismissRequest = { deleteId = null },
        title = { Text("Delete rule?") }, text = { Text("Remove “${deleting.name}”? Its location registration will also be removed. Other rules stay in place.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { deleteId = null; onAction(deleting, RuleAction.DELETE) }) { Text("Delete rule", color = Danger) } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Keep rule") } })
    LazyColumn(Modifier.fillMaxSize().testTag("rulesList"), contentPadding = PaddingValues(DKSpace.screen),
        verticalArrangement = Arrangement.spacedBy(DKSpace.gap)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rules", style = MaterialTheme.typography.headlineLarge)
                    Text("Your automatic blocking routines.", color = Muted, fontSize = 13.sp)
                }
                FilledIconButton(enabled = !busy, onClick = onCreateRule) { Icon(Icons.Rounded.Add, "Create") }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuleFilter.entries.forEach { option -> FilterChip(filter == option, { filter = option }, label = { Text(option.label) }) }
            }
            Text("Active filters by trigger. Each rule shows its enforcement status.", color = Muted, fontSize = 11.sp)
        }
        if (visible.isEmpty()) item {
            if (plans.isEmpty()) EmptyRuleCard(onCreateRule)
            else Text("No rules match this filter.", color = Muted, modifier = Modifier.padding(vertical = 24.dp))
        }
        items(visible, key = { it.id }) { plan ->
            RuleManagementCard(plan, statuses.getValue(plan.id), busy, { onEdit(plan) },
                { action -> if (action == RuleAction.DELETE) deleteId = plan.id else onAction(plan, action) })
        }
    }
}

@Composable
internal fun RuleManagementCard(plan: BlockPlan, status: RuleStatus, busy: Boolean,
                                onEdit: () -> Unit, onAction: (RuleAction) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val tint = when (plan.triggerType) {
        TriggerType.DAILY_LIMIT -> Amber
        TriggerType.LOCATION -> Green2
        TriggerType.SCHEDULE -> Green
        TriggerType.MANUAL -> Muted
    }
    GlowCard {
        Column(Modifier.fillMaxWidth().padding(DKSpace.card).testTag("rule:${plan.id}"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(tint.copy(alpha = .1f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text(ruleEmoji(plan), fontSize = 24.sp) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp).clickable(enabled = !busy, onClick = onEdit)) {
                    Text(plan.name, fontWeight = FontWeight.Bold)
                    Text("${appCount(plan.packages.size)} · ${plan.triggerType.name.lowercase().replace('_', ' ')}", color = Muted, fontSize = 12.sp)
                }
                Box {
                    IconButton(enabled = !busy, onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Actions for ${plan.name}") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onAction(RuleAction.DUPLICATE) })
                        DropdownMenuItem(text = { Text("Delete", color = Danger) }, onClick = { menu = false; onAction(RuleAction.DELETE) })
                    }
                }
            }
            Text(ruleSummary(plan), color = Muted, fontSize = 13.sp)
            HorizontalDivider(color = Line)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (plan.enabled) "Enabled" else "Disabled", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(status.label, fontSize = 12.sp, color = if (status.triggerActive && status.operational) Green else Muted)
                }
                Switch(plan.enabled, onCheckedChange = { onAction(RuleAction.TOGGLE) }, enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = "Enable ${plan.name}" },
                    colors = SwitchDefaults.colors(checkedTrackColor = Green))
            }
        }
    }
}
