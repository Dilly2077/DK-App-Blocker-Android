package com.dk.appblocker

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.time.LocalDate
import kotlin.math.roundToInt

private val Navy = Color(0xFF06142E)
private val DeepNavy = Color(0xFF020817)
private val Accent = Color(0xFF2F9BFF)
private val Purple = Color(0xFF8A3FFC)
private val Card = Color(0xFF151A24)
private val Muted = Color(0xFF94A0B4)
private val Success = Color(0xFF2DD47B)

@Composable
fun DKTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Accent,
        secondary = Purple,
        background = Color.Black,
        surface = Card,
        surfaceVariant = Color(0xFF202631),
        onPrimary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White,
        onSurfaceVariant = Color(0xFFB4BECC)
    )
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DKTheme { AppRoot() } }
    }
}

private enum class Tab(val title: String, val icon: ImageVector) {
    HOME("Blocking", Icons.Rounded.Shield),
    RULES("Rules", Icons.Rounded.Tune),
    INSIGHTS("Insights", Icons.Rounded.BarChart),
    SETTINGS("Settings", Icons.Rounded.Settings)
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.HOME) }
    var plans by remember { mutableStateOf(Prefs.getPlans(context).toList()) }
    var editor by remember { mutableStateOf<BlockPlan?>(null) }
    var creating by remember { mutableStateOf(false) }
    var permissionTick by remember { mutableIntStateOf(0) }

    fun reload() {
        plans = Prefs.getPlans(context).toList()
        permissionTick++
    }

    if (creating || editor != null) {
        RuleEditor(
            initial = editor,
            onCancel = { creating = false; editor = null },
            onSave = { plan ->
                val mutable = Prefs.getPlans(context)
                val index = mutable.indexOfFirst { it.id == plan.id }
                if (index >= 0) mutable[index] = plan else mutable.add(plan)
                Prefs.savePlans(context, mutable)
                GeofenceManager.sync(context, mutable)
                plans = mutable.toList()
                creating = false
                editor = null
            }
        )
        return
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF05070B)) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.title, maxLines = 1) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.RULES) {
                FloatingActionButton(
                    onClick = { creating = true },
                    containerColor = Accent,
                    contentColor = Color.White
                ) { Icon(Icons.Rounded.Add, "Add rule") }
            }
        }
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Navy, DeepNavy, Color.Black), endY = 900f))
                .padding(inner)
        ) {
            when (tab) {
                Tab.HOME -> HomeScreen(plans, permissionTick, { tab = Tab.RULES }, ::reload)
                Tab.RULES -> RulesScreen(
                    plans = plans,
                    onToggle = { plan, enabled ->
                        val updated = plans.map { if (it.id == plan.id) it.copy(enabled = enabled) else it }
                        Prefs.savePlans(context, updated)
                        GeofenceManager.sync(context, updated)
                        plans = updated
                    },
                    onEdit = { editor = it },
                    onDelete = { plan ->
                        val updated = plans.filterNot { it.id == plan.id }
                        Prefs.savePlans(context, updated)
                        Prefs.setActiveGeofence(context, plan.id, false)
                        GeofenceManager.sync(context, updated)
                        plans = updated
                    }
                )
                Tab.INSIGHTS -> InsightsScreen(permissionTick)
                Tab.SETTINGS -> SettingsScreen(permissionTick, ::reload)
            }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp)) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun HomeScreen(
    plans: List<BlockPlan>,
    permissionTick: Int,
    onGoRules: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val accessibility = remember(permissionTick) { isAccessibilityEnabled(context) }
    val usage = remember(permissionTick) { hasUsageAccess(context) }
    var focusEnd by remember { mutableLongStateOf(Prefs.focusEnd(context)) }
    val minuteKey = System.currentTimeMillis() / 60_000L
    val activePlans = remember(plans, permissionTick, minuteKey) {
        plans.filter { it.enabled && RuleEngine.isPlanActive(context, it) }
    }
    val todayEvents = remember(permissionTick, plans) {
        val start = startOfTodayMillis()
        Prefs.getBlockEvents(context).count { it.timestamp >= start }
    }

    LaunchedEffect(focusEnd) {
        while (focusEnd > System.currentTimeMillis()) {
            kotlinx.coroutines.delay(1000)
            focusEnd = Prefs.focusEnd(context)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader("DK App Blocker", "Private, local and built around rules you control") }
        item {
            ElevatedCard(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF101A2B)),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(56.dp).background(if (accessibility) Accent.copy(alpha = .18f) else Color(0x33FFFFFF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Shield, null, tint = if (accessibility) Accent else Muted, modifier = Modifier.size(30.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (accessibility) "Protection active" else "Finish setup", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (accessibility) "${activePlans.size} active rule${if (activePlans.size == 1) "" else "s"} right now"
                                else "Enable Accessibility to enforce blocks",
                                color = Muted
                            )
                        }
                        Box(Modifier.size(12.dp).background(if (accessibility) Success else Color(0xFFFFB020), CircleShape))
                    }
                    if (!accessibility) {
                        Button(
                            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); onRefresh() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Enable Accessibility") }
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard("Blocked today", todayEvents.toString(), Icons.Rounded.Block, Modifier.weight(1f))
                MetricCard("Rules", plans.count { it.enabled }.toString(), Icons.Rounded.Tune, Modifier.weight(1f))
            }
        }
        item {
            Text("Quick focus", modifier = Modifier.padding(horizontal = 24.dp), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(10.dp))
            ElevatedCard(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Card),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val remaining = (focusEnd - System.currentTimeMillis()).coerceAtLeast(0)
                    if (remaining > 0) {
                        Text("Focus is running", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${remaining / 60_000}m ${(remaining / 1000) % 60}s remaining", color = Accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { Prefs.stopFocus(context); focusEnd = 0 }) { Text("End session") }
                    } else {
                        Text("Instantly block every app already included in your rules.", color = Muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(25, 45, 60).forEach { min ->
                                AssistChip(
                                    onClick = {
                                        val packages = plans.flatMap { it.packages }.toSet()
                                        if (packages.isNotEmpty()) {
                                            Prefs.startFocus(context, packages, min)
                                            focusEnd = Prefs.focusEnd(context)
                                        }
                                    },
                                    label = { Text("${min}m") },
                                    leadingIcon = { Icon(Icons.Rounded.Timer, null, modifier = Modifier.size(18.dp)) }
                                )
                            }
                        }
                        if (plans.flatMap { it.packages }.isEmpty()) {
                            TextButton(onClick = onGoRules) { Text("Add a rule first") }
                        }
                    }
                }
            }
        }
        if (!usage) {
            item {
                PermissionPrompt(
                    title = "Usage access recommended",
                    body = "Needed for daily app limits and screen-time insights.",
                    button = "Grant usage access",
                    onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); onRefresh() }
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Active now", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onGoRules) { Text("All rules") }
            }
        }
        if (activePlans.isEmpty()) {
            item {
                Text(
                    "No scheduled, limit, manual or location rule is currently active.",
                    color = Muted,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        } else {
            items(activePlans.take(4), key = { it.id }) { CompactRuleCard(it) }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    ElevatedCard(modifier, colors = CardDefaults.elevatedCardColors(containerColor = Card), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = Accent)
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(title, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PermissionPrompt(title: String, body: String, button: String, onClick: () -> Unit) {
    ElevatedCard(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF1A1F2A)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(4.dp))
            Text(body, color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onClick) { Text(button) }
        }
    }
}

@Composable
private fun CompactRuleCard(plan: BlockPlan) {
    ElevatedCard(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Card),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TriggerBadge(plan.triggerType)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(plan.name, fontWeight = FontWeight.Bold)
                Text(ruleSummary(plan), color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${plan.packages.size} apps", color = Accent, fontSize = 13.sp)
        }
    }
}

@Composable
private fun TriggerBadge(type: TriggerType) {
    Box(
        Modifier.size(44.dp).background(Accent.copy(alpha = .16f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) { Icon(triggerIcon(type), null, tint = Accent) }
}

private fun ruleSummary(plan: BlockPlan): String = when (plan.triggerType) {
    TriggerType.SCHEDULE -> "${formatMinute(plan.startMinute)}–${formatMinute(plan.endMinute)} · ${plan.days.size} day${if (plan.days.size == 1) "" else "s"}"
    TriggerType.DAILY_LIMIT -> "After ${plan.dailyLimitMinutes} minutes per day"
    TriggerType.LOCATION -> "Within ${plan.radiusMeters.roundToInt()} m of saved place"
    TriggerType.MANUAL -> if (plan.manualActive) "Manual block active" else "Manual block paused"
}

private fun formatMinute(value: Int): String = "%02d:%02d".format((value / 60) % 24, value % 60)

@Composable
private fun RulesScreen(
    plans: List<BlockPlan>,
    onToggle: (BlockPlan, Boolean) -> Unit,
    onEdit: (BlockPlan) -> Unit,
    onDelete: (BlockPlan) -> Unit
) {
    var deleting by remember { mutableStateOf<BlockPlan?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenHeader("Blocking rules", "Mix schedules, app limits, places and manual locks") }
        if (plans.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Shield, null, tint = Accent, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No rules yet", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text("Tap + to create your first block.", color = Muted)
                }
            }
        } else {
            items(plans, key = { it.id }) { plan ->
                ElevatedCard(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp).clickable { onEdit(plan) },
                    colors = CardDefaults.elevatedCardColors(containerColor = Card),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TriggerBadge(plan.triggerType)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(plan.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                                    if (plan.strict) Icon(Icons.Rounded.Lock, "Strict", tint = Purple, modifier = Modifier.size(18.dp))
                                }
                                Text(ruleSummary(plan), color = Muted, fontSize = 13.sp)
                            }
                            Switch(checked = plan.enabled, onCheckedChange = { onToggle(plan, it) })
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = .06f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${plan.packages.size} selected app${if (plan.packages.size == 1) "" else "s"}", color = Muted, modifier = Modifier.weight(1f))
                            IconButton(onClick = { deleting = plan }) { Icon(Icons.Rounded.DeleteOutline, "Delete", tint = Color(0xFFFF6B6B)) }
                            IconButton(onClick = { onEdit(plan) }) { Icon(Icons.Rounded.Edit, "Edit") }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { plan ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${plan.name}?") },
            text = { Text("This cannot be undone.") },
            confirmButton = { TextButton(onClick = { onDelete(plan); deleting = null }) { Text("Delete", color = Color(0xFFFF6B6B)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun RuleEditor(initial: BlockPlan?, onCancel: () -> Unit, onSave: (BlockPlan) -> Unit) {
    val context = LocalContext.current
    var plan by remember { mutableStateOf(initial ?: BlockPlan()) }
    var showApps by remember { mutableStateOf(false) }
    var savingLocation by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    val fineLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        locationMessage = if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) "Location permission granted" else "Location permission is required for place rules"
    }

    if (showApps) {
        AppPicker(plan.packages.toSet(), { showApps = false }) { selected ->
            plan = plan.copy(packages = selected.toList())
            showApps = false
        }
        return
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "New blocking rule" else "Edit rule") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Close") } },
                actions = {
                    TextButton(enabled = plan.name.isNotBlank() && plan.packages.isNotEmpty(), onClick = { onSave(plan) }) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy)
            )
        }
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Navy, DeepNavy, Color.Black))).padding(inner),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                OutlinedTextField(
                    value = plan.name,
                    onValueChange = { plan = plan.copy(name = it.take(40)) },
                    label = { Text("Rule name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }
            item {
                SectionCard("Trigger") {
                    TriggerType.entries.forEach { type ->
                        FilterChip(
                            selected = plan.triggerType == type,
                            onClick = { plan = plan.copy(triggerType = type) },
                            label = { Text(triggerTitle(type)) },
                            leadingIcon = { Icon(triggerIcon(type), null, modifier = Modifier.size(18.dp)) }
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
            item {
                SectionCard("Apps") {
                    Column(Modifier.fillMaxWidth()) {
                        Text("${plan.packages.size} selected", color = Muted)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { showApps = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Apps, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Choose apps")
                        }
                    }
                }
            }
            when (plan.triggerType) {
                TriggerType.SCHEDULE -> item {
                    SectionCard("Schedule") {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TimeButton("Starts", plan.startMinute, Modifier.weight(1f)) { plan = plan.copy(startMinute = it) }
                                TimeButton("Ends", plan.endMinute, Modifier.weight(1f)) { plan = plan.copy(endMinute = it) }
                            }
                            Text("Days", color = Muted, fontSize = 13.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, label ->
                                    val day = index + 1
                                    FilterChip(
                                        selected = day in plan.days,
                                        onClick = {
                                            val days = plan.days.toMutableSet()
                                            if (day in days) days.remove(day) else days.add(day)
                                            plan = plan.copy(days = days)
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }
                TriggerType.DAILY_LIMIT -> item {
                    SectionCard("Daily app limit") {
                        Column(Modifier.fillMaxWidth()) {
                            Text("${plan.dailyLimitMinutes} minutes", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Accent)
                            Slider(
                                value = plan.dailyLimitMinutes.toFloat(),
                                onValueChange = { plan = plan.copy(dailyLimitMinutes = (it / 5).roundToInt() * 5) },
                                valueRange = 5f..240f,
                                steps = 46
                            )
                            Text("Blocks the selected apps once their combined use today reaches this limit.", color = Muted, fontSize = 13.sp)
                        }
                    }
                }
                TriggerType.LOCATION -> item {
                    SectionCard("Place") {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                if (plan.latitude == 0.0 && plan.longitude == 0.0) "No place saved" else "Saved: %.5f, %.5f".format(plan.latitude, plan.longitude),
                                color = if (plan.latitude == 0.0 && plan.longitude == 0.0) Muted else Color.White
                            )
                            Text("Radius: ${plan.radiusMeters.roundToInt()} m", color = Accent, fontWeight = FontWeight.Bold)
                            Slider(
                                value = plan.radiusMeters,
                                onValueChange = { plan = plan.copy(radiusMeters = it.roundToInt().toFloat()) },
                                valueRange = 100f..1000f
                            )
                            Button(
                                enabled = !savingLocation,
                                onClick = {
                                    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    if (!hasFine) {
                                        fineLocationLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                                    } else {
                                        savingLocation = true
                                        val client = LocationServices.getFusedLocationProviderClient(context)
                                        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                                            .addOnSuccessListener { location ->
                                                savingLocation = false
                                                if (location != null) {
                                                    plan = plan.copy(latitude = location.latitude, longitude = location.longitude)
                                                    locationMessage = "Current location saved"
                                                } else locationMessage = "Could not get a location fix"
                                            }
                                            .addOnFailureListener {
                                                savingLocation = false
                                                locationMessage = "Location failed: ${it.localizedMessage ?: "unknown error"}"
                                            }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.MyLocation, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (savingLocation) "Finding location…" else "Use current location")
                            }
                            locationMessage?.let { Text(it, color = Muted, fontSize = 13.sp) }
                            Text("For reliable background place rules on Android 10+, set Location permission to ‘Allow all the time’ in App info.", color = Muted, fontSize = 12.sp)
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Open app permissions") }
                        }
                    }
                }
                TriggerType.MANUAL -> item {
                    SectionCard("Manual block") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Block now", fontWeight = FontWeight.Bold)
                                Text("Keep these apps blocked until you switch this off.", color = Muted, fontSize = 13.sp)
                            }
                            Switch(checked = plan.manualActive, onCheckedChange = { plan = plan.copy(manualActive = it) })
                        }
                    }
                }
            }
            item {
                SectionCard("Strict options") {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingSwitch("Strict rule", "Use the global Strict Mode PIN before a temporary break.", plan.strict) {
                            plan = plan.copy(strict = it)
                        }
                        SettingSwitch("Allow 5-minute breaks", "Show a temporary bypass on the blocked screen.", plan.allowBreaks) {
                            plan = plan.copy(allowBreaks = it)
                        }
                    }
                }
            }
        }
    }
}

private fun triggerTitle(type: TriggerType) = when (type) {
    TriggerType.SCHEDULE -> "Schedule"
    TriggerType.DAILY_LIMIT -> "Daily limit"
    TriggerType.LOCATION -> "Location"
    TriggerType.MANUAL -> "Manual"
}

private fun triggerIcon(type: TriggerType): ImageVector = when (type) {
    TriggerType.SCHEDULE -> Icons.Rounded.Schedule
    TriggerType.DAILY_LIMIT -> Icons.Rounded.HourglassBottom
    TriggerType.LOCATION -> Icons.Rounded.LocationOn
    TriggerType.MANUAL -> Icons.Rounded.ToggleOn
}

@Composable
private fun SectionCard(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = Card),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        FlowRow(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun TimeButton(label: String, minute: Int, modifier: Modifier = Modifier, onTime: (Int) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = { TimePickerDialog(context, { _, hour, min -> onTime(hour * 60 + min) }, minute / 60, minute % 60, true).show() },
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(label, fontSize = 11.sp, color = Muted)
            Text(formatMinute(minute), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AppPicker(selected: Set<String>, onBack: () -> Unit, onDone: (Set<String>) -> Unit) {
    val context = LocalContext.current
    val apps = remember { installedLaunchableApps(context) }
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(selected) }
    val filtered = remember(apps, query) { apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) } }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Choose apps") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } },
                actions = { TextButton(onClick = { onDone(chosen) }) { Text("Done (${chosen.size})", fontWeight = FontWeight.Bold) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy)
            )
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Navy, DeepNavy, Color.Black))).padding(inner)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(18.dp)
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { chosen = apps.map { it.packageName }.toSet() }, label = { Text("Select all") })
                AssistChip(onClick = { chosen = emptySet() }, label = { Text("Clear") })
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.packageName }) { app ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            chosen = if (app.packageName in chosen) chosen - app.packageName else chosen + app.packageName
                        }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(42.dp).background(Accent.copy(alpha = .16f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                            Text(app.label.take(1).uppercase(), color = Accent, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, fontWeight = FontWeight.SemiBold)
                            Text(app.packageName, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Checkbox(checked = app.packageName in chosen, onCheckedChange = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightsScreen(permissionTick: Int) {
    val context = LocalContext.current
    val hasUsage = remember(permissionTick) { hasUsageAccess(context) }
    val rows = remember(permissionTick, hasUsage) { if (hasUsage) RuleEngine.topUsageToday(context) else emptyList() }
    val events = remember(permissionTick) { Prefs.getBlockEvents(context) }
    val total = rows.sumOf { it.millis }
    val today = LocalDate.now()
    val counts = (6 downTo 0).map { offset ->
        val day = today.minusDays(offset.toLong())
        events.count { epochDay(it.timestamp) == day }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader("Insights", "Screen time and blocking stay on this device") }
        item {
            ElevatedCard(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Card),
                shape = RoundedCornerShape(26.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("TODAY", color = Accent, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(if (hasUsage) formatDuration(total) else "—", fontSize = 42.sp, fontWeight = FontWeight.Bold)
                    Text("screen time", color = Muted)
                    Spacer(Modifier.height(20.dp))
                    Text("Blocked attempts · 7 days", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    BlockChart(counts)
                }
            }
        }
        if (!hasUsage) {
            item {
                PermissionPrompt("Usage access required", "Android only exposes per-app screen time after you grant special Usage Access.", "Open Usage Access") {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
        } else {
            item { Text("Most used apps", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp)) }
            items(rows.take(10), key = { it.packageName }) { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(Color.White.copy(alpha = .08f), CircleShape), contentAlignment = Alignment.Center) {
                        Text(row.label.take(1).uppercase(), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(row.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatDuration(row.millis), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BlockChart(values: List<Int>) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column {
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            val gap = 12.dp.toPx()
            val width = (size.width - gap * 6) / 7
            values.forEachIndexed { index, value ->
                val height = (size.height * value / max.toFloat()).coerceAtLeast(if (value > 0) 5.dp.toPx() else 0f)
                drawRoundRect(
                    color = if (index == 6) Accent else Accent.copy(alpha = .35f),
                    topLeft = Offset(index * (width + gap), size.height - height),
                    size = Size(width, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, color = Muted, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun SettingsScreen(permissionTick: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    var strict by remember(permissionTick) { mutableStateOf(Prefs.getStrict(context)) }
    var showPin by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    val accessibility = remember(permissionTick) { isAccessibilityEnabled(context) }
    val usage = remember(permissionTick) { hasUsageAccess(context) }
    val fine = remember(permissionTick) { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED }
    val fineLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onChanged() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader("Settings", "No account, subscription, ads or telemetry") }
        item {
            SettingsCard("Permissions") {
                PermissionRow("Accessibility", "Required to detect and block a foreground app", accessibility) {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                PermissionRow("Usage access", "Required for daily limits and screen-time insights", usage) {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                PermissionRow("Location", "Only needed for place-based rules", fine) {
                    fineLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                }
            }
        }
        item {
            SettingsCard("Strict Mode") {
                SettingSwitch("Enable Strict Mode", "Adds PIN protection to temporary breaks on rules marked strict.", strict.enabled) {
                    strict = strict.copy(enabled = it)
                    Prefs.saveStrict(context, strict)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showPin = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Lock, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (strict.pinHash.isBlank()) "Set Strict Mode PIN" else "Change Strict Mode PIN")
                }
            }
        }
        item {
            SettingsCard("Privacy & data") {
                Text("Everything is stored locally in Android SharedPreferences. No analytics SDK, ad SDK, account system or network API is included.", color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("DK App Blocker config", Prefs.exportJson(context)))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.ContentCopy, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (copied) "Copied configuration" else "Copy configuration JSON")
                }
            }
        }
        item {
            SettingsCard("About") {
                Text("DK App Blocker", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Version 0.1.0", color = Muted)
                Spacer(Modifier.height(8.dp))
                Text("Local-first Android blocking with premium-style rule types available without a subscription.", color = Muted, fontSize = 14.sp)
            }
        }
    }

    if (showPin) {
        AlertDialog(
            onDismissRequest = { showPin = false; newPin = "" },
            title = { Text("Set PIN") },
            text = {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter(Char::isDigit).take(8) },
                    label = { Text("4–8 digits") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newPin.length in 4..8,
                    onClick = {
                        strict = strict.copy(pinHash = Prefs.hashPin(newPin))
                        Prefs.saveStrict(context, strict)
                        showPin = false
                        newPin = ""
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showPin = false; newPin = "" }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
    ElevatedCard(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Card),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun PermissionRow(title: String, body: String, granted: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).background(if (granted) Success.copy(alpha = .14f) else Color.White.copy(alpha = .06f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (granted) Icons.Rounded.Check else Icons.Rounded.ChevronRight, null, tint = if (granted) Success else Muted)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingSwitch(title: String, body: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
