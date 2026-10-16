package com.dk.appblocker

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
@Composable
internal fun QuickFocusCard(tick: Long, accessibility: Boolean, onStrict: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val session = remember(tick, revision) { Prefs.focusSession(context) }
    val elapsed = android.os.SystemClock.elapsedRealtime()
    val boot = Prefs.boot(context)
    val left = session?.remaining(elapsed, boot) ?: 0L
    val progress = session?.progress(elapsed, boot) ?: 0f
    var hours by rememberSaveable { mutableStateOf("0") }
    var minutes by rememberSaveable { mutableStateOf("25") }
    var selection by remember { mutableStateOf(Prefs.focusSelection(context)) }
    var showApps by remember { mutableStateOf(false) }
    val duration = focusDurationMillis(hours, minutes)
    if (showApps) AppPickerDialog(selection, { showApps = false }) { chosen ->
        showApps = false
        scope.launch {
            busy = true
            val saved = withContext(Dispatchers.IO) { Prefs.saveFocusSelection(context, chosen) }
            if (saved) selection = chosen else error = "Could not save app selection. Please retry."
            busy = false
        }
    }
    LabeledCard("Quick focus", if (session != null) "Current session" else "One session. Fewer distractions.") {
        if (session != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FocusRing(progress)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 180.dp)) {
                    Text(formatFocusCountdown(left), color = Green, fontWeight = FontWeight.Bold, fontSize = 28.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, textAlign = TextAlign.Center, modifier = Modifier.testTag("focusCountdown"))
                    Text("${(progress * 100).toInt()}% complete", color = Muted, fontSize = 12.sp)
                }
            }
            Text("${session.packages.size} ${if (session.packages.size == 1) "app" else "apps"} in this session", fontWeight = FontWeight.SemiBold)
            FocusAppList(session.packages)
            Text(if (accessibility) "Blocking enabled for this session" else "Timer only · blocking unavailable",
                color = if (accessibility) Green else Amber, fontSize = 12.sp, modifier = Modifier.testTag("focusEnforcementStatus"))
            if (!accessibility) TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Set up blocking") }
            Button(enabled = !busy, onClick = {
                if (!Prefs.canEditRules(context)) onStrict() else scope.launch {
                    busy = true
                    val saved = withContext(Dispatchers.IO) { Prefs.stopFocus(context) }
                    if (!saved) error = "Could not end focus. Check Strict Mode or retry saving."
                    revision++; busy = false
                }
            }, modifier = Modifier.fillMaxWidth().testTag("endFocus")) { Text(if (busy) "Ending…" else "End Focus") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(hours, { hours = it; error = null }, label = { Text("Hours") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = duration == null,
                    modifier = Modifier.weight(1f).testTag("focusHours"))
                OutlinedTextField(minutes, { minutes = it; error = null }, label = { Text("Minutes") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = duration == null,
                    modifier = Modifier.weight(1f).testTag("focusMinutes"))
            }
            if (duration == null) Text("Enter a positive duration using whole numbers within the supported numeric range.", color = Danger, fontSize = 12.sp)
            else Text("Duration: ${formatFocusCountdown(duration)}", color = Muted, fontSize = 12.sp)
            OutlinedButton(enabled = !busy, onClick = { showApps = true }) { Text("Choose apps (${selection.size})") }
            FocusAppList(selection)
            if (!accessibility) Text("Timer only until blocking is enabled.", color = Amber, fontSize = 12.sp)
            Button(enabled = !busy && selection.isNotEmpty() && duration != null, onClick = {
                if (!Prefs.canEditRules(context)) onStrict() else scope.launch {
                    busy = true
                    val result = withContext(Dispatchers.IO) { Prefs.startFocusSession(context, selection, duration ?: 0L) }
                    error = when (result) {
                        FocusStartResult.STARTED -> null
                        FocusStartResult.ALREADY_ACTIVE -> "A session is already running. End it before starting another."
                        FocusStartResult.INVALID_DURATION -> "Duration is too large or invalid. Choose a shorter duration."
                        FocusStartResult.NO_APPS -> "Choose at least one app. Recovery apps cannot be blocked."
                        FocusStartResult.LOCKED -> "Strict Mode currently prevents starting focus."
                        FocusStartResult.STORAGE_ERROR -> "Could not save the session. Please retry."
                    }
                    revision++; busy = false
                }
            }, modifier = Modifier.fillMaxWidth().testTag("startFocus")) { Text(if (busy) "Saving…" else "Start Focus") }
        }
        error?.let { Text(it, color = Danger, fontSize = 12.sp) }
    }
}

@Composable
internal fun FocusAppList(packages: Set<String>) {
    val context = LocalContext.current
    val labels = remember(packages) {
        packages.sorted().map { pkg ->
            val label = runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            label to pkg
        }
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    if (packages.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { AppIconStrip(packages, maxIcons = if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) 2 else 3) }
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("focusAppListToggle")) { Text(if (expanded) "Hide apps" else "Show apps") }
        }
    }
    if (expanded) labels.forEach { (label, pkg) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(pkg)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(label, fontSize = 13.sp)
                if (label != pkg) Text(pkg, color = Muted, fontSize = 11.sp)
            }
        }
    }
}
@Composable
internal fun FocusRing(progress: Float) {
    Box(Modifier.size(190.dp).testTag("focusProgress").progressSemantics(progress), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Color(0xFF123C28), -90f, 360f, false, Offset(stroke / 2, stroke / 2), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Brush.sweepGradient(listOf(Green2, Green, Green2)), -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(stroke / 2, stroke / 2), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}
