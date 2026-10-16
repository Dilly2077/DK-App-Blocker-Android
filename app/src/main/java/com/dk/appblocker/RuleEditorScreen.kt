package com.dk.appblocker

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
@Composable
internal fun CreateRuleTypeScreen(onBack: () -> Unit, onType: (TriggerType) -> Unit, onStrict: () -> Unit) {
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
            item { RuleTypeButton("🔒", "Strict Mode", "Protect rule changes with recoverable verification") { onStrict() } }
        }
    }
}

@Composable
internal fun RuleTypeButton(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel2)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 30.sp); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 12.sp) }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

@Composable
internal fun RuleEditorScreen(initial: BlockPlan, saving: Boolean = false, onBack: () -> Unit, onSave: (BlockPlan) -> Unit,
                             error: String? = null, existing: Boolean = false) {
    val context = LocalContext.current
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var selectedApps by rememberSaveable(initial.id) { mutableStateOf(initial.packages.toSet()) }
    var enabled by rememberSaveable(initial.id) { mutableStateOf(initial.enabled) }
    var strict by rememberSaveable(initial.id) { mutableStateOf(initial.strict) }
    var allowBreaks by rememberSaveable(initial.id) { mutableStateOf(initial.allowBreaks) }
    var days by rememberSaveable(initial.id) { mutableStateOf(initial.days) }
    var startMinute by rememberSaveable(initial.id) { mutableIntStateOf(initial.startMinute) }
    var endMinute by rememberSaveable(initial.id) { mutableIntStateOf(initial.endMinute) }
    var limit by rememberSaveable(initial.id) { mutableIntStateOf(initial.dailyLimitMinutes) }
    var lat by rememberSaveable(initial.id) { mutableDoubleStateOf(initial.latitude) }
    var lon by rememberSaveable(initial.id) { mutableDoubleStateOf(initial.longitude) }
    var radius by rememberSaveable(initial.id) { mutableFloatStateOf(initial.radiusMeters) }
    var manualActive by rememberSaveable(initial.id) { mutableStateOf(initial.manualActive) }
    var showApps by rememberSaveable(initial.id) { mutableStateOf(false) }
    var locationMessage by rememberSaveable(initial.id) { mutableStateOf("") }
    var locationConfigured by rememberSaveable(initial.id) { mutableStateOf(initial.locationConfigured || lat != 0.0 || lon != 0.0) }

    fun fetchLocation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        locationMessage = "Finding your location…"
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { loc ->
                if (loc != null) { lat = loc.latitude; lon = loc.longitude; locationConfigured = true; locationMessage = "Location selected; save the rule to apply it" }
                else locationMessage = "Location unavailable"
            }
            .addOnFailureListener { locationMessage = "Could not get location" }
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true) fetchLocation() else locationMessage = "Precise location is required" }

    ScreenBackground {
        Column(Modifier.fillMaxSize().testTag("ruleEditor")) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = !saving, onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text(if (existing) "Edit rule" else "New rule", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("${ruleEmoji(initial)}  ${initial.triggerType.name.lowercase().replace('_', ' ')}", color = Muted, fontSize = 12.sp)
                }
            }
            Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                error?.let { Text(it, color = Danger, modifier = Modifier.testTag("storageError")) }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Rule name") }, modifier = Modifier.fillMaxWidth().testTag("ruleName"), singleLine = true)
                SettingCard("📱", "Apps to block", if (selectedApps.isEmpty()) "Choose apps" else "${appCount(selectedApps.size)} selected") { showApps = true }
                when (initial.triggerType) {
                    TriggerType.DAILY_LIMIT -> LabeledCard("Daily limit", "Block the selected apps after this much combined use each day.") {
                        Text("$limit minutes", color = Green, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Slider(limit.toFloat(), { limit = it.roundToInt().coerceIn(5, 240) }, valueRange = 5f..240f)
                    }
                    TriggerType.SCHEDULE -> LabeledCard("When to block", "Choose days and start/end time.") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            (1..7).forEach { day ->
                                val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
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
                        Button(onClick = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) fetchLocation() else locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("📍 Use my current location") }
                        Text("After granting precise location, open Android Settings → Permissions → Location → Allow all the time for background geofences.", color = Muted, fontSize = 12.sp)
                        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Open location permissions") }
                        if (locationMessage.isNotBlank()) Text(locationMessage, color = Muted, fontSize = 12.sp)
                        Text("Radius ${radius.roundToInt()}m"); Slider(radius, { radius = it }, valueRange = 100f..1000f)
                    }
                    TriggerType.MANUAL -> LabeledCard("Focus mode", "Turn this block on or off manually.") {
                        ToggleRow("Active now", manualActive) { manualActive = it }
                    }
                }
                LabeledCard("Protection", "Optional controls for harder-to-bypass rules.") {
                    ToggleRow("Rule enabled", enabled) { enabled = it }
                    ToggleRow("Require password for breaks", strict) { strict = it }
                    ToggleRow("Allow 5-minute breaks", allowBreaks) { allowBreaks = it }
                    if (strict && allowBreaks) Text("This rule requires your saved password even when global Strict Mode is off. Set it in Settings → Strict Mode. Active global Strict Mode may impose biometrics or a timer instead.", color = Muted, fontSize = 12.sp)
                    if (strict && allowBreaks && Prefs.getStrict(context).pinHash.isBlank()) Text("Set a password in Settings → Strict Mode before saving this rule with breaks, or disable breaks.", color = Danger, fontSize = 12.sp)
                }
                Button(
                    onClick = { onSave(initial.copy(name = name.ifBlank { initial.name }, packages = selectedApps.toList(), enabled = enabled, strict = strict, allowBreaks = allowBreaks, days = days, startMinute = startMinute, endMinute = endMinute, dailyLimitMinutes = limit, latitude = lat, longitude = lon, radiusMeters = radius, manualActive = manualActive, locationConfigured = locationConfigured)) },
                    enabled = !saving && selectedApps.isNotEmpty() && (!strict || !allowBreaks || Prefs.getStrict(context).pinHash.isNotBlank()) && (initial.triggerType != TriggerType.LOCATION || locationConfigured) && (initial.triggerType != TriggerType.SCHEDULE || days.isNotEmpty()),
                    modifier = Modifier.fillMaxWidth().heightIn(min = DKSpace.control),
                    colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black),
                    shape = RoundedCornerShape(18.dp)
                ) { Text(if (saving) "Saving…" else "Save Rule", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (showApps) AppPickerDialog(selectedApps, { showApps = false }) { selectedApps = it; showApps = false }
}
@Composable
internal fun AppPickerDialog(selected: Set<String>, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    val context = LocalContext.current
    val apps by produceState(emptyList<InstalledApp>(), context) { value = withContext(Dispatchers.IO) { installedLaunchableApps(context) } }
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

internal fun showTimePicker(context: Context, minute: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(context, { _, hour, min -> onPicked(hour * 60 + min) }, (minute / 60) % 24, minute % 60, DateFormat.is24HourFormat(context)).show()
}
