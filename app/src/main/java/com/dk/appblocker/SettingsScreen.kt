package com.dk.appblocker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SettingsScreen(tick: Long, onStrict: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permissions = rememberBlockingPermissions(tick)
    val strictSnapshot by produceState<StrictSnapshot?>(null, tick) { value = withContext(Dispatchers.IO) { Prefs.strictSnapshot(context) } }
    val fine = remember(tick) { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED }
    val admin = remember(tick) { isDeviceAdminActive(context) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { GeofenceManager.sync(context) }
    var pendingSettings by remember { mutableStateOf<Intent?>(null) }
    var exporting by remember { mutableStateOf(false) }
    fun openSettings(intent: Intent) {
        val strict = Prefs.getStrict(context)
        if (strict.enabled && strict.blockDeviceSettings) pendingSettings = intent else context.startActivity(intent)
    }
    pendingSettings?.let { destination ->
        AlertDialog(onDismissRequest = { pendingSettings = null }, title = { Text("Open Android Settings?") },
            text = { Text("Strict Mode is active. Recovery settings always remain available.") },
            confirmButton = { TextButton(onClick = { pendingSettings = null; context.startActivity(destination) }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { pendingSettings = null }) { Text("Cancel") } })
    }
    LazyColumn(Modifier.fillMaxSize().testTag("settingsList"), contentPadding = PaddingValues(DKSpace.screen),
        verticalArrangement = Arrangement.spacedBy(DKSpace.gap)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineLarge); Text("Local tools, no subscription", color = Muted, fontSize = 13.sp) }
        item { SectionLabel("Protection") }
        item { SettingCard("🔒", "Strict Mode", when {
            strictSnapshot == null -> "Reading saved protection…"
            strictSnapshot?.recoveryPending == true -> "Recovery active · storage cleanup pending"
            strictSnapshot?.settings?.enabled == true -> "Active · verification required"
            else -> "Choose recoverable protections"
        }, onStrict) }
        item { SectionLabel("Permissions") }
        item { PermissionStatusRow("🛡️", "Accessibility", permissions.accessibility,
            if (permissions.operational) "Service connected · app blocking enabled" else "Required for blocking · service not connected") { openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }
        item { PermissionStatusRow("📊", "Usage Access", permissions.usage, "Only daily limits and screen-time insights") { openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } }
        item { PermissionStatusRow("📍", "Precise location", fine, "Only location-based rules") {
            locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } }
        item { PermissionStatusRow("🧭", "Background location", GeofenceManager.hasPermissions(context), Prefs.geofenceStatus(context)) {
            openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        } }
        item { PermissionStatusRow("🔐", "Device Administrator", admin, "Optional removal confirmation; uninstall remains possible") {
            if (admin) removeDeviceAdmin(context) else requestDeviceAdmin(context)
        } }
        item { SectionLabel("Appearance") }
        item { SettingCard("🎨", "Blocking screen", "Your colours, heading and message") { context.startActivity(Intent(context, BlockScreenSettingsActivity::class.java)) } }
        item { SectionLabel("Data") }
        item { SettingCard("📋", if (exporting) "Preparing export…" else "Export local configuration", "Rules and appearance · excludes credentials and history") {
            if (!exporting) scope.launch {
                exporting = true
                val data = withContext(Dispatchers.IO) { Prefs.exportJson(context) }
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("DK App Blocker configuration", data))
                exporting = false
                android.widget.Toast.makeText(context, "Configuration copied", android.widget.Toast.LENGTH_SHORT).show()
            }
        } }
        item { SectionLabel("About") }
        item { LabeledCard("DK App Blocker · 0.4.0", "Built for focus. Stored on your device.") {
            Text("No accounts, advertisements or subscriptions. Rules, credentials, appearance and history remain local.", color = Muted, fontSize = 13.sp)
            Text("Device Administrator cannot prevent uninstalling. Android Settings, Home and emergency recovery stay reachable.", color = Muted, fontSize = 12.sp)
        } }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text.uppercase(), color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
}

@Composable
internal fun PermissionCard(title: String, body: String, button: String, onClick: () -> Unit) {
    LabeledCard(title, body) { Button(onClick = onClick) { Text(button) } }
}

@Composable
internal fun PermissionStatusRow(emoji: String, title: String, granted: Boolean, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(DKSpace.radius), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(DKSpace.card), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 22.sp)
                Text(title, modifier = Modifier.weight(1f).padding(horizontal = 10.dp), fontWeight = FontWeight.SemiBold)
                Text(if (granted) "Granted" else "Set up", color = if (granted) Green else Amber, fontSize = 12.sp)
            }
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
    }
}
