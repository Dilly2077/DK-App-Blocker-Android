package com.dk.appblocker

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private val Ink = Color(0xFF020604)
private val Ink2 = Color(0xFF07110C)
private val Panel = Color(0xFF0E1713)
private val Panel2 = Color(0xFF111D18)
private val Line = Color(0xFF1B3A2B)
private val Green = Color(0xFF35F47A)
private val Green2 = Color(0xFF8BFFA8)
private val Muted = Color(0xFF98A69E)
private val Danger = Color(0xFFFF625C)

@Composable
fun DKTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Green,
            secondary = Green2,
            background = Ink,
            surface = Panel,
            surfaceVariant = Panel2,
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Muted,
            error = Danger
        ),
        typography = Typography(),
        content = content
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DKTheme { AppRoot() } }
    }
}

private enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    RULES("Rules", Icons.Rounded.FormatListBulleted),
    INSIGHTS("Insights", Icons.Rounded.BarChart),
    PROFILE("Profile", Icons.Rounded.Person)
}

private enum class Route { MAIN, CREATE_TYPE, EDITOR, STRICT }

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    var route by remember { mutableStateOf(Route.MAIN) }
    var tab by remember { mutableStateOf(MainTab.HOME) }
    var plans by remember { mutableStateOf(Prefs.getPlans(context).toList()) }
    var editing by remember { mutableStateOf<BlockPlan?>(null) }
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            tick = System.currentTimeMillis()
        }
    }

    BackHandler(enabled = route != Route.MAIN || tab != MainTab.HOME) {
        when (route) {
            Route.EDITOR -> route = Route.CREATE_TYPE
            Route.CREATE_TYPE, Route.STRICT -> route = Route.MAIN
            Route.MAIN -> tab = MainTab.HOME
        }
    }

    fun reloadPlans() {
        plans = Prefs.getPlans(context).toList()
    }

    when (route) {
        Route.CREATE_TYPE -> CreateRuleTypeScreen(
            onBack = { route = Route.MAIN },
            onType = { type -> editing = defaultPlanFor(type); route = Route.EDITOR },
            onStrict = { route = Route.STRICT }
        )
        Route.EDITOR -> RuleEditorScreen(
            initial = editing ?: defaultPlanFor(TriggerType.SCHEDULE),
            onBack = { route = Route.CREATE_TYPE },
            onSave = { plan ->
                val updated = Prefs.getPlans(context)
                val index = updated.indexOfFirst { it.id == plan.id }
                if (index >= 0) updated[index] = plan else updated.add(plan)
                Prefs.savePlans(context, updated)
                GeofenceManager.sync(context, updated)
                plans = updated.toList()
                editing = null
                tab = MainTab.RULES
                route = Route.MAIN
            }
        )
        Route.STRICT -> StrictModeScreen(
            onBack = { route = Route.MAIN },
            onNavigate = { chosen -> tab = chosen; route = Route.MAIN }
        )
        Route.MAIN -> MainShell(
            tab = tab,
            plans = plans,
            tick = tick,
            onTab = { chosen ->
                val strict = Prefs.getStrict(context)
                if (chosen == MainTab.RULES && strict.enabled && strict.blockRuleChanges) route = Route.STRICT else tab = chosen
            },
            onCreateRule = { route = Route.CREATE_TYPE },
            onEdit = { plan ->
                val strict = Prefs.getStrict(context)
                if (strict.enabled && strict.blockRuleChanges) route = Route.STRICT
                else { editing = plan; route = Route.EDITOR }
            },
            onStrict = { route = Route.STRICT },
            onReload = ::reloadPlans
        )
    }
}

private fun defaultPlanFor(type: TriggerType): BlockPlan = when (type) {
    TriggerType.DAILY_LIMIT -> BlockPlan(name = "App Limit", triggerType = type, dailyLimitMinutes = 30)
    TriggerType.SCHEDULE -> BlockPlan(name = "Schedule", triggerType = type, startMinute = 9 * 60, endMinute = 17 * 60)
    TriggerType.LOCATION -> BlockPlan(name = "Location", triggerType = type, radiusMeters = 200f)
    TriggerType.MANUAL -> BlockPlan(name = "Focus Mode", triggerType = type, manualActive = true)
}

@Composable
private fun MainShell(
    tab: MainTab,
    plans: List<BlockPlan>,
    tick: Long,
    onTab: (MainTab) -> Unit,
    onCreateRule: () -> Unit,
    onEdit: (BlockPlan) -> Unit,
    onStrict: () -> Unit,
    onReload: () -> Unit
) {
    Scaffold(containerColor = Ink, bottomBar = { MainBottomBar(tab, onTab) }) { inner ->
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Ink2, Ink, Color.Black), endY = 900f))
                .padding(inner)
        ) {
            when (tab) {
                MainTab.HOME -> HomeScreen(plans, tick, onCreateRule, onEdit, onStrict)
                MainTab.RULES -> RulesScreen(plans, onCreateRule, onEdit, onReload)
                MainTab.INSIGHTS -> InsightsScreen(tick)
                MainTab.PROFILE -> ProfileScreen(tick, onStrict)
            }
        }
    }
}

@Composable
private fun MainBottomBar(selected: MainTab, onTab: (MainTab) -> Unit) {
    NavigationBar(containerColor = Color(0xFF050906), tonalElevation = 0.dp) {
        MainTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onTab(tab) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Green,
                    selectedTextColor = Green,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = Color(0xFF98A19C),
                    unselectedTextColor = Color(0xFF98A19C)
                ),
                icon = { Icon(tab.icon, null) },
                label = { Text(tab.label, fontSize = 11.sp) }
            )
        }
    }
}

@Composable
private fun ScreenBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF03100A), Ink, Color.Black), endY = 820f)
        ),
        content = content
    )
}

@Composable
private fun HomeScreen(
    plans: List<BlockPlan>,
    tick: Long,
    onCreateRule: () -> Unit,
    onEdit: (BlockPlan) -> Unit,
    onStrict: () -> Unit
) {
    val context = LocalContext.current
    val activePlans = remember(plans, tick / 30_000L) { plans.filter { it.enabled && RuleEngine.isPlanActive(context, it) } }
    val accessReady = remember(tick / 3000L) { isAccessibilityEnabled(context) }
    val greeting = remember {
        when (java.time.LocalTime.now().hour) {
            in 0..11 -> "Good morning,"
            in 12..17 -> "Good afternoon,"
            else -> "Good evening,"
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(greeting, color = Muted, fontSize = 14.sp)
                    Text("Stay focused\ntoday 🍃", fontWeight = FontWeight.Bold, fontSize = 29.sp, lineHeight = 31.sp)
                    Text("Small steps. Big progress.", color = Muted, fontSize = 13.sp)
                }
                Box(Modifier.size(38.dp).background(Color(0xFF174F2E), CircleShape), contentAlignment = Alignment.Center) {
                    Text("D", fontWeight = FontWeight.Bold)
                }
            }
        }
        if (!accessReady) {
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF17140C))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🛡️ Finish blocking setup", fontWeight = FontWeight.Bold)
                        Text("Android Accessibility access is what lets DK App Blocker put the blocking screen over selected apps.", color = Muted, fontSize = 12.sp)
                        Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Enable blocking") }
                    }
                }
            }
        }
        item {
            GlowCard(onClick = onCreateRule) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Focus Mode", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).background(Green, CircleShape))
                            Spacer(Modifier.width(7.dp))
                            Text(if (activePlans.isNotEmpty()) "Active" else "Ready", color = Green, fontWeight = FontWeight.SemiBold)
                        }
                        Text("Blocking distractions across your device", color = Muted, fontSize = 13.sp)
                    }
                    FocusRing(if (activePlans.isNotEmpty()) 1f else .78f)
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Active Rules", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onCreateRule) { Text("See all", color = Green) }
            }
        }
        if (plans.isEmpty()) item { EmptyRuleCard(onCreateRule) }
        else items(plans.take(5), key = { it.id }) { plan ->
            HomeRuleRow(plan, plan in activePlans) { if (plan.name.contains("Strict", true)) onStrict() else onEdit(plan) }
        }
    }
}

@Composable
private fun GlowCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Panel2),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
    ) { Column(content = content) }
}

@Composable
private fun FocusRing(progress: Float) {
    Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Color(0xFF123C28), -90f, 360f, false, Offset(stroke / 2, stroke / 2), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Brush.sweepGradient(listOf(Green2, Green, Green2)), -90f, 360f * progress.coerceIn(.08f, 1f), false, Offset(stroke / 2, stroke / 2), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("🍃", fontSize = 28.sp)
    }
}

@Composable
private fun EmptyRuleCard(onCreateRule: () -> Unit) {
    GlowCard(onCreateRule) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🌱", fontSize = 28.sp); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Create your first rule", fontWeight = FontWeight.Bold)
                Text("Add an app limit, schedule or location block.", color = Muted, fontSize = 13.sp)
            }
            Icon(Icons.Rounded.AddCircle, null, tint = Green)
        }
    }
}

@Composable
private fun HomeRuleRow(plan: BlockPlan, active: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF17241E))
    ) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(ruleEmoji(plan), fontSize = 28.sp); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(plan.name, fontWeight = FontWeight.SemiBold)
                Text(ruleSummary(plan), color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Surface(shape = RoundedCornerShape(999.dp), color = if (active) Color(0xFF123D25) else Color(0xFF242A27)) {
                Text(if (active) "Active" else if (!plan.enabled) "Disabled" else "Ready", color = if (active) Green else Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
            Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(20.dp))
        }
    }
}

private fun ruleEmoji(plan: BlockPlan): String = when (plan.triggerType) {
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

private fun ruleSummary(plan: BlockPlan): String = when (plan.triggerType) {
    TriggerType.DAILY_LIMIT -> "${plan.dailyLimitMinutes}m daily • ${plan.packages.size} apps"
    TriggerType.SCHEDULE -> "${formatMinute(plan.startMinute)} – ${formatMinute(plan.endMinute)} • ${daysLabel(plan.days)}"
    TriggerType.LOCATION -> "Within ${plan.radiusMeters.roundToInt()}m • ${plan.packages.size} apps"
    TriggerType.MANUAL -> if (plan.manualActive) "Manual focus is on" else "Manual focus is off"
}

private fun daysLabel(days: Set<Int>): String = when {
    days.size == 7 -> "Every day"
    days == setOf(1, 2, 3, 4, 5) -> "Mon–Fri"
    days.isEmpty() -> "No days"
    else -> days.sorted().joinToString(" ") { listOf("M", "T", "W", "T", "F", "S", "S")[it - 1] }
}

private fun formatMinute(minute: Int): String = "%02d:%02d".format((minute / 60) % 24, minute % 60)

@Composable
private fun RulesScreen(plans: List<BlockPlan>, onCreateRule: () -> Unit, onEdit: (BlockPlan) -> Unit, onReload: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rules", fontWeight = FontWeight.Bold, fontSize = 28.sp)
                    Text("Your automatic blocking routines.", color = Muted, fontSize = 13.sp)
                }
                FilledIconButton(onClick = onCreateRule, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Green, contentColor = Color.Black)) { Icon(Icons.Rounded.Add, "Create") }
            }
        }
        if (plans.isEmpty()) item { EmptyRuleCard(onCreateRule) }
        else items(plans, key = { it.id }) { plan ->
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(ruleEmoji(plan), fontSize = 28.sp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).clickable { onEdit(plan) }) {
                        Text(plan.name, fontWeight = FontWeight.Bold)
                        Text(ruleSummary(plan), color = Muted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = plan.enabled,
                        onCheckedChange = { enabled ->
                            val strict = Prefs.getStrict(context)
                            if (!strict.enabled || !strict.blockRuleChanges) {
                                val updated = plans.map { if (it.id == plan.id) it.copy(enabled = enabled) else it }
                                Prefs.savePlans(context, updated); GeofenceManager.sync(context, updated); onReload()
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Green)
                    )
                    IconButton(onClick = { onEdit(plan) }) { Icon(Icons.Rounded.ChevronRight, null) }
                }
            }
        }
    }
}

@Composable
private fun CreateRuleTypeScreen(onBack: () -> Unit, onType: (TriggerType) -> Unit, onStrict: () -> Unit) {
    ScreenBackground {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                    Spacer(Modifier.weight(1f)); Text("Create Rule", fontWeight = FontWeight.Bold, fontSize = 18.sp); Spacer(Modifier.weight(1f)); Spacer(Modifier.width(48.dp))
                }
            }
            item { Text("What do you want to block?", fontWeight = FontWeight.Bold, fontSize = 22.sp); Text("Choose a rule type to get started.", color = Muted, fontSize = 13.sp) }
            item { RuleTypeButton("⏰", "App Limit", "Set a daily time limit for apps") { onType(TriggerType.DAILY_LIMIT) } }
            item { RuleTypeButton("☀️", "Schedule", "Block apps at specific times") { onType(TriggerType.SCHEDULE) } }
            item { RuleTypeButton("📍", "Location", "Block apps at certain places") { onType(TriggerType.LOCATION) } }
            item { RuleTypeButton("🍃", "Focus Mode", "Create distraction-free sessions") { onType(TriggerType.MANUAL) } }
            item { RuleTypeButton("🔒", "Strict Mode", "Lock settings and prevent bypass") { onStrict() } }
        }
    }
}

@Composable
private fun RuleTypeButton(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel2)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 30.sp); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 12.sp) }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

@Composable
private fun RuleEditorScreen(initial: BlockPlan, onBack: () -> Unit, onSave: (BlockPlan) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial.name) }
    var selectedApps by remember { mutableStateOf(initial.packages.toSet()) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var strict by remember { mutableStateOf(initial.strict) }
    var allowBreaks by remember { mutableStateOf(initial.allowBreaks) }
    var days by remember { mutableStateOf(initial.days) }
    var startMinute by remember { mutableIntStateOf(initial.startMinute) }
    var endMinute by remember { mutableIntStateOf(initial.endMinute) }
    var limit by remember { mutableIntStateOf(initial.dailyLimitMinutes) }
    var lat by remember { mutableDoubleStateOf(initial.latitude) }
    var lon by remember { mutableDoubleStateOf(initial.longitude) }
    var radius by remember { mutableFloatStateOf(initial.radiusMeters) }
    var manualActive by remember { mutableStateOf(initial.manualActive) }
    var showApps by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf("") }

    fun fetchLocation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        locationMessage = "Finding your location…"
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { loc ->
                if (loc != null) { lat = loc.latitude; lon = loc.longitude; locationMessage = "Location saved" }
                else locationMessage = "Location unavailable"
            }
            .addOnFailureListener { locationMessage = "Could not get location" }
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) fetchLocation() }

    ScreenBackground {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Text("${ruleEmoji(initial)}  ${initial.name}", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Rule name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                SettingCard("📱", "Apps to block", if (selectedApps.isEmpty()) "Choose apps" else "${selectedApps.size} apps selected") { showApps = true }
                when (initial.triggerType) {
                    TriggerType.DAILY_LIMIT -> LabeledCard("Daily limit", "Block the selected apps after this much combined use each day.") {
                        Text("$limit minutes", color = Green, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Slider(limit.toFloat(), { limit = it.roundToInt().coerceIn(5, 240) }, valueRange = 5f..240f)
                    }
                    TriggerType.SCHEDULE -> LabeledCard("When to block", "Choose days and start/end time.") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            items((1..7).toList()) { day ->
                                val labels = listOf("M", "T", "W", "T", "F", "S", "S")
                                FilterChip(selected = day in days, onClick = { days = if (day in days) days - day else days + day }, label = { Text(labels[day - 1]) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { showTimePicker(context, startMinute) { startMinute = it } }, modifier = Modifier.weight(1f)) { Text("Start ${formatMinute(startMinute)}") }
                            OutlinedButton(onClick = { showTimePicker(context, endMinute) { endMinute = it } }, modifier = Modifier.weight(1f)) { Text("End ${formatMinute(endMinute)}") }
                        }
                    }
                    TriggerType.LOCATION -> LabeledCard("Location block", "Block selected apps inside this radius.") {
                        Text(if (lat == 0.0 && lon == 0.0) "No location saved" else "${"%.5f".format(lat)}, ${"%.5f".format(lon)}", color = if (lat == 0.0 && lon == 0.0) Muted else Green)
                        Button(onClick = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) fetchLocation() else locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) { Text("📍 Use my current location") }
                        if (locationMessage.isNotBlank()) Text(locationMessage, color = Muted, fontSize = 12.sp)
                        Text("Radius ${radius.roundToInt()}m"); Slider(radius, { radius = it }, valueRange = 100f..1000f)
                    }
                    TriggerType.MANUAL -> LabeledCard("Focus mode", "Turn this block on or off manually.") {
                        ToggleRow("Active now", manualActive) { manualActive = it }
                    }
                }
                LabeledCard("Protection", "Optional controls for harder-to-bypass rules.") {
                    ToggleRow("Rule enabled", enabled) { enabled = it }
                    ToggleRow("Strict block", strict) { strict = it }
                    ToggleRow("Allow 5-minute breaks", allowBreaks) { allowBreaks = it }
                }
                Button(
                    onClick = { onSave(initial.copy(name = name.ifBlank { initial.name }, packages = selectedApps.toList(), enabled = enabled, strict = strict, allowBreaks = allowBreaks, days = days, startMinute = startMinute, endMinute = endMinute, dailyLimitMinutes = limit, latitude = lat, longitude = lon, radiusMeters = radius, manualActive = manualActive)) },
                    enabled = selectedApps.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black),
                    shape = RoundedCornerShape(18.dp)
                ) { Text("Save Rule", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (showApps) AppPickerDialog(selectedApps, { showApps = false }) { selectedApps = it; showApps = false }
}

@Composable
private fun SettingCard(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel2)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 28.sp); Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 12.sp) }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

@Composable
private fun LabeledCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(subtitle, color = Muted, fontSize = 12.sp); content()
        }
    }
}

@Composable
private fun ToggleRow(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text(title, modifier = Modifier.weight(1f)); Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Green)) }
}

@Composable
private fun AppPickerDialog(selected: Set<String>, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    val context = LocalContext.current
    val apps = remember { installedLaunchableApps(context) }
    var chosen by remember { mutableStateOf(selected) }
    var search by remember { mutableStateOf("") }
    val filtered = remember(search, apps) { apps.filter { search.isBlank() || it.label.contains(search, true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose apps") },
        text = {
            Column(Modifier.heightIn(max = 520.dp)) {
                OutlinedTextField(search, { search = it }, placeholder = { Text("Search apps") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(Modifier.fillMaxWidth().clickable { chosen = if (app.packageName in chosen) chosen - app.packageName else chosen + app.packageName }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(app.packageName in chosen, { checked -> chosen = if (checked) chosen + app.packageName else chosen - app.packageName })
                            Column { Text(app.label); Text(app.packageName, color = Muted, fontSize = 10.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(chosen) }) { Text("Done", color = Green) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun showTimePicker(context: Context, minute: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(context, { _, hour, min -> onPicked(hour * 60 + min) }, (minute / 60) % 24, minute % 60, DateFormat.is24HourFormat(context)).show()
}

@Composable
private fun StrictModeScreen(onBack: () -> Unit, onNavigate: (MainTab) -> Unit) {
    val context = LocalContext.current
    var strict by remember { mutableStateOf(Prefs.getStrict(context)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showPasswordSetup by remember { mutableStateOf(false) }
    var showPasswordUnlock by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            now = System.currentTimeMillis()
            val latest = Prefs.getStrict(context)
            if (latest != strict) strict = latest
        }
    }

    val adminActive = isDeviceAdminActive(context)
    val method = strict.unlockMethod ?: "PASSWORD"
    val remaining = (strict.lockUntil - now).coerceAtLeast(0L)

    fun save(updated: StrictSettings) {
        strict = updated
        Prefs.saveStrict(context, updated)
    }

    fun activate() {
        if (strict.preventUninstall && !isDeviceAdminActive(context)) {
            requestDeviceAdmin(context)
            notice = "Enable Device Administrator protection, then tap Activate again."
            return
        }
        if (method == "PASSWORD" && strict.pinHash.isBlank()) {
            showPasswordSetup = true
            notice = "Set a password before activating password verification."
            return
        }
        val until = if (method == "TIMER") now + strict.timerDurationMinutes * 60_000L else 0L
        save(strict.copy(enabled = true, lockUntil = until))
        notice = "Strict Mode activated."
    }

    fun disableVerified() {
        save(strict.copy(enabled = false, lockUntil = 0L))
        notice = "Strict Mode disabled."
    }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF050906)) {
                val navItems = listOf(
                    Triple("Home", Icons.Rounded.Home, MainTab.HOME),
                    Triple("Rules", Icons.Rounded.FormatListBulleted, MainTab.RULES),
                    Triple("Strict Mode", Icons.Rounded.Lock, null),
                    Triple("Insights", Icons.Rounded.BarChart, MainTab.INSIGHTS),
                    Triple("Profile", Icons.Rounded.Person, MainTab.PROFILE)
                )
                navItems.forEach { item ->
                    NavigationBarItem(selected = item.third == null, onClick = { item.third?.let(onNavigate) }, icon = { Icon(item.second, null) }, label = { Text(item.first, fontSize = 9.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Color.Transparent))
                }
            }
        }
    ) { inner ->
        ScreenBackground {
            LazyColumn(Modifier.fillMaxSize().padding(inner), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                        Text("Strict Mode", fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Spacer(Modifier.width(48.dp))
                    }
                }
                item { StrictRing(strict.enabled) }
                item {
                    Text(if (strict.enabled) "Active" else "Inactive", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(if (strict.enabled) "🍃 Your focus is protected" else "Choose protections and an unlock method", color = Muted, fontSize = 13.sp)
                }
                item {
                    LabeledCard("Unlock method", "Choose how Strict Mode is allowed to end.") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("TIMER" to "⏳ Timer", "PASSWORD" to "🔑 Password", "BIOMETRIC" to "🫆 Biometrics").forEach { (value, label) ->
                                FilterChip(selected = method == value, enabled = !strict.enabled, onClick = { save(strict.copy(unlockMethod = value)) }, label = { Text(label, fontSize = 11.sp) })
                            }
                        }
                        if (method == "TIMER") {
                            Text(if (strict.enabled) "Ends in ${formatDuration(remaining)}" else "Duration: ${strict.timerDurationMinutes} minutes", color = Green, fontWeight = FontWeight.Bold)
                            if (!strict.enabled) Slider(strict.timerDurationMinutes.toFloat(), { save(strict.copy(timerDurationMinutes = it.roundToInt().coerceIn(5, 240))) }, valueRange = 5f..240f)
                        }
                        if (method == "PASSWORD" && !strict.enabled) {
                            OutlinedButton(onClick = { showPasswordSetup = true }) { Text(if (strict.pinHash.isBlank()) "Set password" else "Change password") }
                        }
                        if (method == "BIOMETRIC") Text("Android will show the system biometric prompt when you try to disable Strict Mode.", color = Muted, fontSize = 12.sp)
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            StrictCheckRow(Icons.Rounded.List, "Lock rule changes in app", strict.blockRuleChanges, !strict.enabled) { save(strict.copy(blockRuleChanges = !strict.blockRuleChanges)) }
                            StrictCheckRow(Icons.Rounded.NoAccounts, "Prevent app uninstalling", strict.preventUninstall, !strict.enabled) {
                                val next = !strict.preventUninstall
                                save(strict.copy(preventUninstall = next))
                                if (next && !isDeviceAdminActive(context)) requestDeviceAdmin(context)
                            }
                            StrictCheckRow(Icons.Rounded.Settings, "Block device settings", strict.blockDeviceSettings, !strict.enabled) { save(strict.copy(blockDeviceSettings = !strict.blockDeviceSettings)) }
                            StrictCheckRow(Icons.Rounded.ViewCarousel, "Block recent apps", strict.blockRecents, !strict.enabled) { save(strict.copy(blockRecents = !strict.blockRecents)) }
                            StrictCheckRow(Icons.Rounded.Splitscreen, "Block split screen", strict.blockSplitScreen, !strict.enabled) { save(strict.copy(blockSplitScreen = !strict.blockSplitScreen)) }
                        }
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel2)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🛡️", fontSize = 25.sp); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text("Uninstall protection", fontWeight = FontWeight.Bold); Text(if (adminActive) "Device Administrator active" else "Device Administrator not enabled", color = Muted, fontSize = 12.sp) }
                            if (!adminActive) TextButton(onClick = { requestDeviceAdmin(context) }) { Text("Enable", color = Green) }
                            else if (!strict.enabled) TextButton(onClick = { removeDeviceAdmin(context); notice = "Device Administrator removal requested." }) { Text("Remove") }
                        }
                    }
                }
                if (notice.isNotBlank()) item { Text(notice, color = if (notice.contains("activated", true) || notice.contains("disabled", true)) Green else Color(0xFFFFC857), fontSize = 12.sp, textAlign = TextAlign.Center) }
                item {
                    Button(
                        onClick = {
                            if (!strict.enabled) activate()
                            else when (method) {
                                "TIMER" -> notice = if (remaining > 0) "Strict Mode will end automatically in ${formatDuration(remaining)}." else { disableVerified(); "" }
                                "BIOMETRIC" -> launchBiometricVerification(context, onSuccess = ::disableVerified, onError = { notice = it })
                                else -> showPasswordUnlock = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E3A22), contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Green)
                    ) {
                        Icon(if (strict.enabled) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp))
                        Text(if (strict.enabled) when (method) { "TIMER" -> "Timer Locked"; "BIOMETRIC" -> "Verify & Disable"; else -> "Verify & Disable" } else "Activate Strict Mode", fontWeight = FontWeight.Bold)
                    }
                }
                item { Text("Device Administrator makes direct uninstall harder. Blocking Settings through Accessibility adds another layer, but Android still allows recovery methods such as safe mode, ADB, or administrator removal outside the app.", color = Muted, fontSize = 11.sp) }
            }
        }
    }

    if (showPasswordSetup) PasswordSetupDialog(strict, { showPasswordSetup = false }) { save(it); showPasswordSetup = false }
    if (showPasswordUnlock) PasswordUnlockDialog({ showPasswordUnlock = false }) { disableVerified(); showPasswordUnlock = false }
}

@Composable
private fun StrictRing(active: Boolean) {
    Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx(); val s = Size(size.width - stroke, size.height - stroke)
            drawCircle(Color(0x2210FF70), radius = size.minDimension / 2.1f)
            drawArc(Color(0xFF16442B), -90f, 360f, false, Offset(stroke / 2, stroke / 2), s, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Brush.sweepGradient(listOf(Green2, Green, Green2)), -90f, if (active) 360f else 110f, false, Offset(stroke / 2, stroke / 2), s, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("🔒", fontSize = 42.sp)
    }
}

@Composable
private fun StrictCheckRow(icon: ImageVector, label: String, checked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (enabled) Color(0xFFD9E2DD) else Muted, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(13.dp))
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp, color = if (enabled) Color.White else Muted)
        Icon(if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (checked) Green else Muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PasswordSetupDialog(strict: StrictSettings, onDismiss: () -> Unit, onSave: (StrictSettings) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Strict Mode password") },
        text = { OutlinedTextField(password, { password = it.take(64) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation(), placeholder = { Text("At least 4 characters") }) },
        confirmButton = { TextButton(onClick = { if (password.length >= 4) onSave(strict.copy(pinHash = Prefs.hashPin(password))) }, enabled = password.length >= 4) { Text("Save", color = Green) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PasswordUnlockDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disable Strict Mode") },
        text = { Column { OutlinedTextField(password, { password = it.take(64); error = false }, singleLine = true, isError = error, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation(), placeholder = { Text("Password") }); if (error) Text("Incorrect password", color = Danger, fontSize = 12.sp) } },
        confirmButton = { TextButton(onClick = { if (Prefs.checkPin(context, password)) onSuccess() else error = true }) { Text("Disable", color = Green) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun InsightsScreen(tick: Long) {
    val context = LocalContext.current
    var selectedDays by remember { mutableIntStateOf(1) }
    val hasAccess = remember(tick / 5000L) { hasUsageAccess(context) }
    val start = remember(selectedDays, tick / 60_000L) { LocalDate.now().minusDays((selectedDays - 1).toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val usage = remember(selectedDays, tick / 60_000L, hasAccess) { if (hasAccess) RuleEngine.topUsage(context, start, tick) else emptyList() }
    val total = usage.sumOf { it.millis }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row { Text("Insights", fontWeight = FontWeight.Bold, fontSize = 28.sp, modifier = Modifier.weight(1f)); Text("📅 Today") } }
        item {
            Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(999.dp)).padding(4.dp)) {
                PeriodPill("Day", selectedDays == 1, Modifier.weight(1f)) { selectedDays = 1 }; PeriodPill("Week", selectedDays == 7, Modifier.weight(1f)) { selectedDays = 7 }; PeriodPill("Month", selectedDays == 30, Modifier.weight(1f)) { selectedDays = 30 }
            }
        }
        if (!hasAccess) item { PermissionCard("Screen-time access required", "Grant Usage Access for app limits and insights.", "Grant access") { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } }
        else {
            item { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) { Column(Modifier.padding(16.dp)) { Text("Screen Time", color = Muted); Text(formatDuration(total), fontSize = 36.sp, fontWeight = FontWeight.Bold); UsageBars(total) } } }
            item { Text("Most used apps", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            if (usage.isEmpty()) item { Text("No usage data yet.", color = Muted) }
            else items(usage.take(8), key = { it.packageName }) { UsageRowCard(it, total) }
        }
    }
}

@Composable
private fun PeriodPill(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(999.dp)).background(if (selected) Green else Color.Transparent).clickable(onClick = onClick).padding(vertical = 9.dp), contentAlignment = Alignment.Center) { Text(text, color = if (selected) Color.Black else Color.White) }
}

@Composable
private fun UsageBars(total: Long) {
    val values = listOf(.55f, .18f, .12f, .3f, .22f, .45f, .32f, .52f, .66f, .41f, .23f, .76f)
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        val gap = size.width / values.size
        values.forEachIndexed { i, v -> val w = gap * .34f; drawRoundRect(if (i == values.lastIndex - 1) Green else Color(0xFF233129), Offset(i * gap + gap * .33f, size.height * (1f - v)), Size(w, size.height * v), androidx.compose.ui.geometry.CornerRadius(w / 2, w / 2)) }
    }
}

@Composable
private fun UsageRowCard(row: UsageRow, total: Long) {
    val fraction = if (total > 0) row.millis.toFloat() / total else 0f
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) { Text(row.label.take(1).uppercase(), color = Color.Black, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(row.label, fontWeight = FontWeight.Bold); LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp), color = Green) }; Spacer(Modifier.width(12.dp)); Text(formatDuration(row.millis))
        }
    }
}

@Composable
private fun ProfileScreen(tick: Long, onStrict: () -> Unit) {
    val context = LocalContext.current
    val accessibility = remember(tick / 3000L) { isAccessibilityEnabled(context) }
    val usage = remember(tick / 3000L) { hasUsageAccess(context) }
    val fine = remember(tick / 3000L) { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED }
    val admin = remember(tick / 3000L) { isDeviceAdminActive(context) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Box(Modifier.size(90.dp).background(Color(0xFF174F2E), CircleShape), contentAlignment = Alignment.Center) { Text("D", fontSize = 48.sp) }; Text("DK", fontSize = 30.sp); Text("All features unlocked", color = Green, fontSize = 12.sp) }
        item { SettingCard("🎨", "Blocked screen", "Change colours, heading and message") { context.startActivity(Intent(context, BlockScreenSettingsActivity::class.java)) } }
        item { SettingCard("🔒", "Strict Mode", if (Prefs.getStrict(context).enabled) "Active" else "Configure advanced protections", onStrict) }
        item { PermissionStatusRow("🛡️", "Accessibility", accessibility, "Needed to enforce app blocking") { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }
        item { PermissionStatusRow("📊", "Usage access", usage, "Needed for app limits and insights") { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } }
        item { PermissionStatusRow("📍", "Precise location", fine, "Needed for location-based rules") { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) } }
        item { PermissionStatusRow("🔐", "Device Administrator", admin, "Used by uninstall protection in Strict Mode") { if (admin) Unit else requestDeviceAdmin(context) } }
        item { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0B2718))) { Column(Modifier.padding(18.dp)) { Text("🌱 Local-first", fontWeight = FontWeight.Bold); Text("Rules, passwords, block-screen customisation and history stay on this device.", color = Muted, fontSize = 12.sp) } } }
    }
}

@Composable
private fun PermissionCard(title: String, body: String, button: String, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel2)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(body, color = Muted, fontSize = 12.sp); Button(onClick = onClick) { Text(button) } } }
}

@Composable
private fun PermissionStatusRow(emoji: String, title: String, granted: Boolean, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 26.sp); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 11.sp) }; Text(if (granted) "Ready" else "Set up", color = if (granted) Green else Color(0xFFFFC857), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}
