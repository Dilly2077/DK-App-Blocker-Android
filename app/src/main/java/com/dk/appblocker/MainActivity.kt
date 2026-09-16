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

    fun reloadPlans() {
        plans = Prefs.getPlans(context).toList()
    }

    when (route) {
        Route.CREATE_TYPE -> CreateRuleTypeScreen(
            onBack = { route = Route.MAIN },
            onType = { type ->
                editing = defaultPlanFor(type)
                route = Route.EDITOR
            },
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
            onEdit = { plan -> editing = plan; route = Route.EDITOR },
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
    Scaffold(
        containerColor = Ink,
        bottomBar = { MainBottomBar(tab, onTab) }
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
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
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF03100A), Ink, Color.Black), endY = 820f)),
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
    val focusRemaining = (Prefs.focusEnd(context) - tick).coerceAtLeast(0L)
    val focusActive = focusRemaining > 0
    val greeting = remember {
        val hour = java.time.LocalTime.now().hour
        when {
            hour < 12 -> "Good morning,"
            hour < 18 -> "Good afternoon,"
            else -> "Good evening,"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(greeting, color = Muted, fontSize = 14.sp)
                    Spacer(Modifier.height(3.dp))
                    Text("Stay focused\ntoday 🍃", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 29.sp, lineHeight = 31.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Small steps. Big progress.", color = Muted, fontSize = 13.sp)
                }
                Box(Modifier.size(38.dp).background(Color(0xFF174F2E), CircleShape), contentAlignment = Alignment.Center) {
                    Text("D", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            GlowCard(onClick = onCreateRule) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Focus Mode", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).background(Green, CircleShape))
                            Spacer(Modifier.width(7.dp))
                            Text(if (focusActive) "Active" else "Ready", color = Green, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(if (focusActive) "${formatDuration(focusRemaining)} remaining" else "Blocking distractions across your device", color = Muted, fontSize = 13.sp)
                    }
                    FocusRing(progress = if (focusActive) ((focusRemaining % 3_600_000L) / 3_600_000f).coerceIn(.12f, 1f) else .78f)
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Rounded.ChevronRight, null, tint = Color.White)
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Active Rules", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onCreateRule) { Text("See all", color = Green) }
            }
        }

        if (plans.isEmpty()) {
            item { EmptyRuleCard(onCreateRule) }
        } else {
            items(plans.take(4), key = { it.id }) { plan ->
                HomeRuleRow(plan, plan in activePlans, onClick = { if (plan.name.contains("Strict", true)) onStrict() else onEdit(plan) })
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun GlowCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
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
            drawArc(
                color = Color(0xFF123C28),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(Green2, Green, Green2)),
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(.08f, 1f),
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Text("🍃", fontSize = 28.sp)
    }
}

@Composable
private fun EmptyRuleCard(onCreateRule: () -> Unit) {
    GlowCard(onClick = onCreateRule) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🌱", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
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
            Text(ruleEmoji(plan), fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(plan.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(ruleSummary(plan), color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (active) {
                Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFF123D25)) {
                    Text("Active", color = Green, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            } else if (!plan.enabled) {
                Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFF242A27)) {
                    Text("Disabled", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            Spacer(Modifier.width(5.dp))
            Icon(Icons.Rounded.ChevronRight, null, tint = Color(0xFFD2D8D4), modifier = Modifier.size(20.dp))
        }
    }
}

private fun ruleEmoji(plan: BlockPlan): String = when (plan.triggerType) {
    TriggerType.DAILY_LIMIT -> "⏰"
    TriggerType.LOCATION -> "📍"
    TriggerType.MANUAL -> if (plan.name.contains("Morning", true)) "🌱" else "🍃"
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
private fun RulesScreen(
    plans: List<BlockPlan>,
    onCreateRule: () -> Unit,
    onEdit: (BlockPlan) -> Unit,
    onReload: () -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rules", fontWeight = FontWeight.Bold, fontSize = 28.sp)
                    Text("Your automatic blocking routines.", color = Muted, fontSize = 13.sp)
                }
                FilledIconButton(onClick = onCreateRule, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Green, contentColor = Color.Black)) {
                    Icon(Icons.Rounded.Add, "Create rule")
                }
            }
        }
        if (plans.isEmpty()) {
            item { EmptyRuleCard(onCreateRule) }
        } else {
            items(plans, key = { it.id }) { plan ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF17241E))
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(ruleEmoji(plan), fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).clickable { onEdit(plan) }) {
                            Text(plan.name, fontWeight = FontWeight.Bold)
                            Text(ruleSummary(plan), color = Muted, fontSize = 12.sp)
                        }
                        Switch(
                            checked = plan.enabled,
                            onCheckedChange = { enabled ->
                                val updated = plans.map { if (it.id == plan.id) it.copy(enabled = enabled) else it }
                                Prefs.savePlans(context, updated)
                                GeofenceManager.sync(context, updated)
                                onReload()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Green)
                        )
                        IconButton(onClick = { onEdit(plan) }) { Icon(Icons.Rounded.ChevronRight, null) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateRuleTypeScreen(onBack: () -> Unit, onType: (TriggerType) -> Unit, onStrict: () -> Unit) {
    ScreenBackground {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                    Spacer(Modifier.weight(1f))
                    Text("Create Rule", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(48.dp))
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text("What do you want to block?", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("Choose a rule type to get started.", color = Muted, fontSize = 13.sp)
            }
            item { RuleTypeButton("⏰", "App Limit", "Set a daily time limit for apps") { onType(TriggerType.DAILY_LIMIT) } }
            item { RuleTypeButton("☀️", "Schedule", "Block apps at specific times") { onType(TriggerType.SCHEDULE) } }
            item { RuleTypeButton("📍", "Location", "Block apps at certain places") { onType(TriggerType.LOCATION) } }
            item { RuleTypeButton("🍃", "Focus Mode", "Create distraction-free sessions") { onType(TriggerType.MANUAL) } }
            item { RuleTypeButton("🔒", "Strict Mode", "Lock settings and prevent bypass") { onStrict() } }
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF082A18)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00B95A))
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("👑", fontSize = 28.sp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Advanced tools", fontWeight = FontWeight.Bold)
                            Text("All premium-style features are included", color = Muted, fontSize = 12.sp)
                        }
                        Icon(Icons.Rounded.CheckCircle, null, tint = Green)
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleTypeButton(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel2),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1C3027))
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 30.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Color.White)
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
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        locationMessage = "Finding your location…"
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    lat = loc.latitude
                    lon = loc.longitude
                    locationMessage = "Location saved"
                } else locationMessage = "Location unavailable. Try again outdoors."
            }
            .addOnFailureListener { locationMessage = "Could not get location" }
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) fetchLocation() else locationMessage = "Location permission is required"
    }

    ScreenBackground {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Text("${ruleEmoji(initial)}  ${initial.name}", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    onSave(initial.copy(name = name.ifBlank { initial.name }, packages = selectedApps.toList(), enabled = enabled, strict = strict, allowBreaks = allowBreaks, days = days, startMinute = startMinute, endMinute = endMinute, dailyLimitMinutes = limit, latitude = lat, longitude = lon, radiusMeters = radius, manualActive = manualActive))
                }, enabled = selectedApps.isNotEmpty()) { Text("Save", color = if (selectedApps.isNotEmpty()) Green else Muted) }
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Rule name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Green, focusedLabelColor = Green)
                )

                SettingCard(icon = "📱", title = "Apps to block", subtitle = if (selectedApps.isEmpty()) "Choose apps" else "${selectedApps.size} apps selected", onClick = { showApps = true })

                when (initial.triggerType) {
                    TriggerType.DAILY_LIMIT -> {
                        LabeledCard("Daily limit", "Block the selected apps after this much combined use each day.") {
                            Text("$limit minutes", color = Green, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Slider(value = limit.toFloat(), onValueChange = { limit = it.roundToInt().coerceIn(5, 240) }, valueRange = 5f..240f, steps = 46)
                        }
                    }
                    TriggerType.SCHEDULE -> {
                        LabeledCard("When to block", "Choose days and a start/end time.") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                items((1..7).toList()) { day ->
                                    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
                                    FilterChip(
                                        selected = day in days,
                                        onClick = { days = if (day in days) days - day else days + day },
                                        label = { Text(labels[day - 1]) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Green, selectedLabelColor = Color.Black)
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = { showTimePicker(context, startMinute) { startMinute = it } }, modifier = Modifier.weight(1f)) { Text("Start ${formatMinute(startMinute)}") }
                                OutlinedButton(onClick = { showTimePicker(context, endMinute) { endMinute = it } }, modifier = Modifier.weight(1f)) { Text("End ${formatMinute(endMinute)}") }
                            }
                        }
                    }
                    TriggerType.LOCATION -> {
                        LabeledCard("Location block", "Block selected apps inside this radius.") {
                            Text(if (lat == 0.0 && lon == 0.0) "No location saved" else "${"%.5f".format(lat)}, ${"%.5f".format(lon)}", color = if (lat == 0.0 && lon == 0.0) Muted else Green)
                            Button(onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) fetchLocation()
                                else locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }) { Text("📍 Use my current location") }
                            if (locationMessage.isNotBlank()) Text(locationMessage, color = Muted, fontSize = 12.sp)
                            Text("Radius ${radius.roundToInt()} m", fontWeight = FontWeight.SemiBold)
                            Slider(value = radius, onValueChange = { radius = it }, valueRange = 100f..1000f)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                Text("For reliable background geofencing, allow location access all the time in Android settings.", color = Muted, fontSize = 12.sp)
                                TextButton(onClick = {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                                }) { Text("Open app permissions", color = Green) }
                            }
                        }
                    }
                    TriggerType.MANUAL -> {
                        LabeledCard("Focus mode", "Turn this block on or off manually.") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Active now", fontWeight = FontWeight.Bold)
                                    Text("The selected apps are blocked while this is on.", color = Muted, fontSize = 12.sp)
                                }
                                Switch(checked = manualActive, onCheckedChange = { manualActive = it }, colors = SwitchDefaults.colors(checkedTrackColor = Green))
                            }
                        }
                    }
                }

                LabeledCard("Protection", "Optional controls for harder-to-bypass rules.") {
                    ToggleRow("Rule enabled", enabled) { enabled = it }
                    ToggleRow("Strict block", strict) { strict = it }
                    ToggleRow("Allow 5-minute breaks", allowBreaks) { allowBreaks = it }
                }

                Button(
                    onClick = {
                        onSave(initial.copy(name = name.ifBlank { initial.name }, packages = selectedApps.toList(), enabled = enabled, strict = strict, allowBreaks = allowBreaks, days = days, startMinute = startMinute, endMinute = endMinute, dailyLimitMinutes = limit, latitude = lat, longitude = lon, radiusMeters = radius, manualActive = manualActive))
                    },
                    enabled = selectedApps.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)
                ) { Text("Save Rule", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    if (showApps) AppPickerDialog(selectedApps, onDismiss = { showApps = false }, onSave = { selectedApps = it; showApps = false })
}

@Composable
private fun SettingCard(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel2),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 28.sp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

@Composable
private fun LabeledCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF18261F))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(subtitle, color = Muted, fontSize = 12.sp)
            content()
        }
    }
}

@Composable
private fun ToggleRow(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked = value, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Green))
    }
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
                OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text("Search apps") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable { chosen = if (app.packageName in chosen) chosen - app.packageName else chosen + app.packageName }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = app.packageName in chosen, onCheckedChange = { checked -> chosen = if (checked) chosen + app.packageName else chosen - app.packageName })
                            Column(Modifier.weight(1f)) {
                                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
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
    var showPinSetup by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }

    fun save(updated: StrictSettings) {
        strict = updated
        Prefs.saveStrict(context, updated)
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
                    NavigationBarItem(
                        selected = item.third == null,
                        onClick = { if (item.third != null) onNavigate(item.third!!) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Color.Transparent),
                        icon = { Icon(item.second, null) },
                        label = { Text(item.first, fontSize = 9.sp, maxLines = 1) }
                    )
                }
            }
        }
    ) { inner ->
        ScreenBackground {
            LazyColumn(
                Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                        Text("Strict Mode", fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        IconButton(onClick = { showPinSetup = true }) { Icon(Icons.Rounded.Settings, "Strict settings") }
                    }
                }
                item { StrictRing(strict.enabled) }
                item {
                    Text(if (strict.enabled) "Active" else "Inactive", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(if (strict.enabled) "🍃  Your focus is protected" else "Turn on strict mode for stronger protection", color = Muted, fontSize = 13.sp)
                }
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Panel),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF183122))
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            StrictCheckRow(Icons.Rounded.List, "Lock rule changes in app", strict.blockRuleChanges) { save(strict.copy(blockRuleChanges = !strict.blockRuleChanges)) }
                            StrictCheckRow(Icons.Rounded.NoAccounts, "Prevent app uninstalling", strict.preventUninstall) { save(strict.copy(preventUninstall = !strict.preventUninstall)) }
                            StrictCheckRow(Icons.Rounded.Settings, "Block device settings", strict.blockDeviceSettings) { save(strict.copy(blockDeviceSettings = !strict.blockDeviceSettings)) }
                            StrictCheckRow(Icons.Rounded.ViewCarousel, "Block recent apps", strict.blockRecents) { save(strict.copy(blockRecents = !strict.blockRecents)) }
                            StrictCheckRow(Icons.Rounded.Splitscreen, "Block split screen", strict.blockSplitScreen) { save(strict.copy(blockSplitScreen = !strict.blockSplitScreen)) }
                        }
                    }
                }
                item {
                    Button(
                        onClick = {
                            if (strict.enabled && strict.pinHash.isNotBlank()) showUnlock = true else save(strict.copy(enabled = !strict.enabled))
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E3A22), contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Green)
                    ) {
                        Icon(if (strict.enabled) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (strict.enabled) "Pause Strict Mode" else "Activate Strict Mode", fontWeight = FontWeight.Bold)
                    }
                }
                item { Text("Strict protections are best-effort on a normal Android installation; Android itself still controls permissions and device-owner capabilities.", color = Muted, fontSize = 11.sp) }
            }
        }
    }

    if (showPinSetup) PinSetupDialog(strict, onDismiss = { showPinSetup = false }, onSave = { save(it); showPinSetup = false })
    if (showUnlock) PinUnlockDialog(onDismiss = { showUnlock = false }, onSuccess = { save(strict.copy(enabled = false)); showUnlock = false })
}

@Composable
private fun StrictRing(active: Boolean) {
    Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val s = Size(size.width - stroke, size.height - stroke)
            drawCircle(color = Color(0x2210FF70), radius = size.minDimension / 2.1f)
            drawArc(color = Color(0xFF16442B), startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = Offset(stroke / 2, stroke / 2), size = s, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(brush = Brush.sweepGradient(listOf(Green2, Green, Green2)), startAngle = -90f, sweepAngle = if (active) 360f else 110f, useCenter = false, topLeft = Offset(stroke / 2, stroke / 2), size = s, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("🔒", fontSize = 42.sp)
    }
}

@Composable
private fun StrictCheckRow(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color(0xFFD9E2DD), modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(13.dp))
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Icon(if (enabled) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (enabled) Green else Muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PinSetupDialog(strict: StrictSettings, onDismiss: () -> Unit, onSave: (StrictSettings) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Strict Mode PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Set a PIN that is required before strict mode can be paused.", color = Muted, fontSize = 13.sp)
                OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(8) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), placeholder = { Text("4–8 digits") })
            }
        },
        confirmButton = { TextButton(onClick = { if (pin.length >= 4) onSave(strict.copy(pinHash = Prefs.hashPin(pin))) }, enabled = pin.length >= 4) { Text("Save", color = Green) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PinUnlockDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pause Strict Mode") },
        text = {
            Column {
                OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(8); error = false }, singleLine = true, isError = error, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), placeholder = { Text("PIN") })
                if (error) Text("Incorrect PIN", color = Danger, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { if (Prefs.checkPin(context, pin)) onSuccess() else error = true }) { Text("Pause", color = Green) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun InsightsScreen(tick: Long) {
    val context = LocalContext.current
    var selectedDays by remember { mutableIntStateOf(1) }
    val hasAccess = remember(tick / 5000L) { hasUsageAccess(context) }
    val end = tick
    val start = remember(selectedDays, tick / 60_000L) { LocalDate.now().minusDays((selectedDays - 1).toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val usage = remember(selectedDays, tick / 60_000L, hasAccess) { if (hasAccess) RuleEngine.topUsage(context, start, end) else emptyList() }
    val total = usage.sumOf { it.millis }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Insights", fontWeight = FontWeight.Bold, fontSize = 28.sp, modifier = Modifier.weight(1f))
                Text("📅  Today", color = Color.White, fontSize = 13.sp)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(999.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PeriodPill("Day", selectedDays == 1, Modifier.weight(1f)) { selectedDays = 1 }
                PeriodPill("Week", selectedDays == 7, Modifier.weight(1f)) { selectedDays = 7 }
                PeriodPill("Month", selectedDays == 30, Modifier.weight(1f)) { selectedDays = 30 }
            }
        }
        if (!hasAccess) {
            item { PermissionCard("Screen-time access required", "Grant Usage Access so Insights and daily app limits can use Android screen-time data.", "Grant access") { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } }
        } else {
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF18261F))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Screen Time", color = Muted, fontSize = 12.sp)
                        Text(formatDuration(total), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        UsageBars(total)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Most used apps", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Text("See all", color = Green, fontSize = 12.sp)
                }
            }
            if (usage.isEmpty()) item { Text("No usage data yet.", color = Muted) }
            else items(usage.take(8), key = { it.packageName }) { row -> UsageRowCard(row, total) }
        }
    }
}

@Composable
private fun PeriodPill(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(999.dp)).background(if (selected) Green else Color.Transparent).clickable(onClick = onClick).padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (selected) Color.Black else Color(0xFFC4CDC8), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
    }
}

@Composable
private fun UsageBars(total: Long) {
    val hours = (total / 3_600_000f).coerceAtLeast(.05f)
    val values = listOf(.55f, .18f, .12f, .3f, .22f, .45f, .32f, .52f, .66f, .41f, .23f, .76f).map { (it * (0.55f + hours / 8f)).coerceIn(.08f, .95f) }
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        val gap = size.width / values.size
        values.forEachIndexed { index, value ->
            val w = gap * .34f
            val left = index * gap + gap * .33f
            drawRoundRect(color = if (index == values.lastIndex - 1) Green else Color(0xFF233129), topLeft = Offset(left, size.height * (1f - value)), size = Size(w, size.height * value), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2, w / 2))
        }
    }
}

@Composable
private fun UsageRowCard(row: UsageRow, total: Long) {
    val fraction = if (total > 0) row.millis.toFloat() / total else 0f
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF17241E))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) { Text(row.label.take(1).uppercase(), color = Color.Black, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp), color = Green, trackColor = Color(0xFF26322C))
            }
            Spacer(Modifier.width(12.dp))
            Text(formatDuration(row.millis), fontSize = 13.sp)
            Icon(Icons.Rounded.ChevronRight, null, tint = Muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ProfileScreen(tick: Long, onStrict: () -> Unit) {
    val context = LocalContext.current
    val accessibility = remember(tick / 3000L) { isAccessibilityEnabled(context) }
    val usage = remember(tick / 3000L) { hasUsageAccess(context) }
    val fine = remember(tick / 3000L) { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED }
    val background = remember(tick / 3000L) { Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Box(Modifier.size(90.dp).background(Color(0xFF6840A4), CircleShape), contentAlignment = Alignment.Center) { Text("D", fontSize = 48.sp) }
            Spacer(Modifier.height(10.dp))
            Text("DK", fontSize = 30.sp, fontWeight = FontWeight.Medium)
            Text("All features unlocked", color = Green, fontSize = 12.sp)
        }
        item { PermissionStatusRow("🛡️", "Accessibility", accessibility, "Needed to enforce app blocking") { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }
        item { PermissionStatusRow("📊", "Usage access", usage, "Needed for app limits and insights") { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } }
        item { PermissionStatusRow("📍", "Precise location", fine, "Needed for location-based rules") { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) } }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            item { PermissionStatusRow("🗺️", "Background location", background, "Needed for reliable geofences") { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) } }
        }
        item { SettingCard("🔒", "Strict Mode", if (Prefs.getStrict(context).enabled) "Active" else "Configure advanced protections", onStrict) }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0B2718)), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF164A2D))) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🌱  Local-first", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("Rules, PIN hashes and block history are kept on this device. No account is required.", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(title: String, body: String, button: String, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel2), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, color = Muted, fontSize = 12.sp)
            Button(onClick = onClick) { Text(button) }
        }
    }
}

@Composable
private fun PermissionStatusRow(emoji: String, title: String, granted: Boolean, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Muted, fontSize = 11.sp)
            }
            Text(if (granted) "Ready" else "Set up", color = if (granted) Green else Color(0xFFFFC857), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ChevronRight, null, tint = Muted, modifier = Modifier.size(18.dp))
        }
    }
}
