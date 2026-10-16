package com.dk.appblocker

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.biometric.BiometricManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
@Composable
internal fun StrictModeScreen(onBack: () -> Unit, onNavigate: (MainTab) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick = rememberVisibleTick()
    var loaded by remember { mutableStateOf(false) }
    var strict by remember { mutableStateOf(StrictSettings()) }
    var recoveryPending by remember { mutableStateOf(false) }
    var draftJson by rememberSaveable { mutableStateOf("") }
    var dirty by rememberSaveable { mutableStateOf(false) }
    val draft = remember(draftJson) { runCatching { com.google.gson.Gson().fromJson(draftJson, StrictSettings::class.java) }.getOrNull() ?: StrictSettings() }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var showPasswordSetup by remember { mutableStateOf(false) }
    var showPasswordUnlock by remember { mutableStateOf(false) }
    fun draft(value: StrictSettings) { draftJson = com.google.gson.Gson().toJson(value.copy(pinHash = "")); dirty = true; notice = null }
    suspend fun refresh() {
        val snapshot = withContext(Dispatchers.IO) { Prefs.strictSnapshot(context) }
        val saved = snapshot.settings
        strict = saved; recoveryPending = snapshot.recoveryPending; loaded = true
        if (!dirty) draftJson = com.google.gson.Gson().toJson(saved.copy(pinHash = ""))
    }
    LaunchedEffect(tick) { if (!busy) refresh() }
    fun persist(operation: () -> Boolean, success: String) {
        if (busy) return
        busy = true; notice = null
        scope.launch {
            val saved = withContext(Dispatchers.IO) { operation() }
            if (saved) { dirty = false; refresh(); notice = success }
            else { refresh(); notice = "Could not persist this change. Previous protection remains in effect. Please retry." }
            busy = false
        }
    }
    fun disableVerified() {
        showPasswordUnlock = false
        persist({ Prefs.disableStrictDurably(context) }, "Strict Mode disabled and cooldown saved.")
    }
    Scaffold(containerColor = Ink, bottomBar = { MainBottomBar(MainTab.SETTINGS, onNavigate) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("strictList"),
            contentPadding = PaddingValues(DKSpace.screen), verticalArrangement = Arrangement.spacedBy(DKSpace.gap)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(enabled = !busy, onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                    Text("Strict Mode", style = MaterialTheme.typography.headlineLarge)
                }
            }
            if (!loaded) item { Text("Loading saved protection…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else {
                item {
                    GlowCard {
                        Column(Modifier.padding(DKSpace.card), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (recoveryPending) "Recovery active · cleanup not saved" else if (strict.enabled) "● Protection active" else "○ Protection inactive",
                                color = if (recoveryPending) Amber else if (strict.enabled) Green else Muted,
                                style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("strictPersistedStatus"))
                            if (recoveryPending) Text("Temporary protection ended for recovery, but storage could not be updated. Retry after storage is available.", color = Amber, fontSize = 12.sp)
                            Text("Saved unlock method: ${strict.unlockMethod?.lowercase()}", color = Muted)
                            if (strict.enabled && strict.unlockMethod == "TIMER")
                                Text(formatFocusCountdown(Prefs.strictRemaining(context)), color = Green, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
                item {
                    LabeledCard("Unlock method", if (strict.enabled) "Verify to change saved protections." else "Choose how this protection can end.") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("TIMER" to "⏳ Timer", "PASSWORD" to "🔑 Password", "BIOMETRIC" to "Biometric").forEach { (value, label) ->
                                FilterChip(draft.unlockMethod == value, { draft(draft.copy(unlockMethod = value)) }, enabled = !strict.enabled && !busy,
                                    label = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (value == "BIOMETRIC") Icon(Icons.Rounded.Fingerprint, null, Modifier.size(16.dp))
                                        Text(label)
                                    } })
                            }
                        }
                        if (!strict.enabled) {
                            if (draft.unlockMethod == "TIMER") {
                                Text("Timer duration: ${draft.timerDurationMinutes} minutes")
                                Slider(draft.timerDurationMinutes.toFloat(), { draft(draft.copy(timerDurationMinutes = it.roundToInt())) },
                                    enabled = !busy, valueRange = 5f..1440f, modifier = Modifier.testTag("strictDuration"))
                            }
                            Text("Rule-edit cooldown: ${draft.editCooldownMinutes} minutes", color = Muted)
                            Slider(draft.editCooldownMinutes.toFloat(), { draft(draft.copy(editCooldownMinutes = it.roundToInt())) },
                                enabled = !busy, valueRange = 0f..60f, modifier = Modifier.testTag("strictCooldown"))
                            OutlinedButton(enabled = !busy, onClick = { showPasswordSetup = true }) { Text(if (strict.pinHash.isBlank()) "Set recovery password" else "Change recovery password") }
                        }
                        if (strict.unlockMethod == "BIOMETRIC" && strict.enabled)
                            OutlinedButton(enabled = !busy, onClick = { showPasswordUnlock = true }) { Text("Use recovery password") }
                    }
                }
                item {
                    LabeledCard("Protections", "Changes stay as a draft until you save.") {
                        ToggleRow("Lock rule changes", draft.blockRuleChanges, !strict.enabled && !busy) { draft(draft.copy(blockRuleChanges = it)) }
                        ToggleRow("Offer administrator confirmation", draft.preventUninstall, !strict.enabled && !busy) { draft(draft.copy(preventUninstall = it)) }
                        ToggleRow("Confirm in-app Settings shortcuts", draft.blockDeviceSettings, !strict.enabled && !busy) { draft(draft.copy(blockDeviceSettings = it)) }
                        ToggleRow("Return home from detected Recents", draft.blockRecents, !strict.enabled && !busy) { draft(draft.copy(blockRecents = it)) }
                        ToggleRow("Return home from detected split controls", draft.blockSplitScreen, !strict.enabled && !busy) { draft(draft.copy(blockSplitScreen = it)) }
                        if (!strict.enabled) {
                            if (dirty) Text("Unsaved changes", color = Amber, modifier = Modifier.testTag("strictUnsaved"))
                            Button(enabled = dirty && !busy, onClick = { persist({ Prefs.configureStrict(context, draft) == PolicyWrite.SAVED }, "Protection settings saved.") },
                                modifier = Modifier.fillMaxWidth().testTag("saveStrictSettings")) { Text("Save protection settings") }
                        }
                    }
                }
                notice?.let { text -> item { Text(text, color = if (text.contains("Could not")) Danger else Muted, modifier = Modifier.testTag("strictNotice")) } }
                item {
                    Button(enabled = !busy && !dirty, modifier = Modifier.fillMaxWidth().heightIn(min = DKSpace.control).testTag("strictActivate"),
                        onClick = {
                            if (!strict.enabled) {
                                if (strict.unlockMethod != "TIMER" && strict.pinHash.isBlank()) showPasswordSetup = true
                                else if (strict.unlockMethod == "BIOMETRIC" && BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS)
                                    notice = "Enrol a strong biometric in Android Settings or choose another method."
                                else persist({ Prefs.activateStrict(context) == PolicyWrite.SAVED }, "Strict Mode activated.")
                            } else when (strict.unlockMethod) {
                                "TIMER" -> notice = "Timer protection ends automatically. Recovery settings remain available."
                                "BIOMETRIC" -> launchBiometricVerification(context, ::disableVerified) { notice = it }
                                else -> showPasswordUnlock = true
                            }
                        }) { Text(if (!strict.enabled) "Activate Strict Mode" else if (strict.unlockMethod == "TIMER") "Timer locked" else "Verify & disable") }
                }
                item {
                    LabeledCard("Recovery & Android limits", "Home, Settings and emergency calling stay available.") {
                        Text("Device Administrator adds a removal confirmation. It cannot make uninstalling impossible. Recents and split-screen behaviour varies by device.", color = Muted, fontSize = 12.sp)
                        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Open Android Accessibility") }
                        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }) { Text("Open Android Settings") }
                        if (isDeviceAdminActive(context)) TextButton(onClick = { removeDeviceAdmin(context); notice = "Administrator removal requested." }) { Text("Remove Device Administrator") }
                        else TextButton(onClick = { requestDeviceAdmin(context) }) { Text("Enable Device Administrator") }
                        Text("Temporary timers expire after reboot for recovery.", color = Muted, fontSize = 12.sp)
                        if (Prefs.editLockedUntil(context) > tick) Text("Rule edits unlock in ${formatDuration(Prefs.editLockedUntil(context) - tick)}", color = Amber)
                    }
                }
            }
        }
    }
    if (showPasswordSetup) PasswordSetupDialog(strict, { showPasswordSetup = false }) { showPasswordSetup = false; notice = "Recovery password saved." }
    if (showPasswordUnlock) PasswordUnlockDialog({ showPasswordUnlock = false }, ::disableVerified)
}

@Composable
internal fun PasswordSetupDialog(strict: StrictSettings, onDismiss: () -> Unit, onSave: (StrictSettings) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Strict Mode password") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (strict.pinHash.isNotBlank()) OutlinedTextField(current, { current = it.take(64); error = false }, label = { Text("Current password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(password, { password = it.take(64) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation(), placeholder = { Text("New password, at least 4 characters") })
            if (error) Text("Password incorrect, temporarily locked, or storage unavailable. Please retry.", color = Danger)
        } },
        confirmButton = { TextButton(onClick = {
            busy = true
            scope.launch {
                val changed = withContext(Dispatchers.IO) { Prefs.replacePin(context, current, password) }
                busy = false
                if (changed) onSave(Prefs.getStrict(context)) else error = true
            }
        }, enabled = password.length >= 4 && !busy) { Text(if (busy) "Saving…" else "Save", color = Green) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun PasswordUnlockDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disable Strict Mode") },
        text = { Column { OutlinedTextField(password, { password = it.take(64); error = false }, singleLine = true, isError = error, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation(), placeholder = { Text("Password") }); if (error) Text("Incorrect password or temporarily locked. Five failures require a 30-second wait.", color = Danger, fontSize = 12.sp) } },
        confirmButton = { TextButton(onClick = {
            busy = true
            scope.launch {
                val valid = withContext(Dispatchers.Default) { Prefs.checkPin(context, password) }
                busy = false
                if (valid) onSuccess() else error = true
            }
        }, enabled = !busy) { Text(if (busy) "Verifying…" else "Disable", color = Green) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
