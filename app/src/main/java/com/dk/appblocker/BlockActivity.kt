package com.dk.appblocker

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class BlockActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PACKAGE = "blocked_package"
        const val EXTRA_REASON = "blocked_reason"
        const val EXTRA_STRICT = "strict"
        const val EXTRA_ALLOW_BREAKS = "allow_breaks"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun render() {
        val blockedPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "Blocked by a rule"
        val strict = intent.getBooleanExtra(EXTRA_STRICT, false)
        val allowBreaks = intent.getBooleanExtra(EXTRA_ALLOW_BREAKS, true)
        val label = runCatching {
            val info = packageManager.getApplicationInfo(blockedPackage, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(blockedPackage.ifBlank { "This app" })

        setContent {
            DKTheme {
                BackHandler { goHome() }
                BlockedScreen(
                    appLabel = label,
                    reason = reason,
                    strict = strict,
                    allowBreaks = allowBreaks,
                    onHome = ::goHome,
                    onBreakGranted = {
                        if (blockedPackage.isNotBlank()) {
                            Prefs.allowPackageUntil(this, blockedPackage, System.currentTimeMillis() + 5 * 60_000L)
                        }
                        finish()
                    }
                )
            }
        }
    }
}

private fun colorFromHex(hex: String, fallback: Int): Color =
    runCatching { Color(AndroidColor.parseColor(hex)) }.getOrElse { Color(fallback) }

@Composable
private fun BlockedScreen(
    appLabel: String,
    reason: String,
    strict: Boolean,
    allowBreaks: Boolean,
    onHome: () -> Unit,
    onBreakGranted: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { Prefs.getBlockScreen(context) }
    val strictSettings = remember { Prefs.getStrict(context) }
    var showPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var biometricError by remember { mutableStateOf("") }

    val background = colorFromHex(settings.backgroundHex, 0xFF020604.toInt())
    val accent = colorFromHex(settings.accentHex, 0xFF35F47A.toInt())
    val message = settings.message
        .replace("{app}", appLabel)
        .replace("{reason}", reason)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(background, background.copy(alpha = .96f), Color.Black)))
            .padding(28.dp)
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                Modifier.size(120.dp).background(accent.copy(alpha = .15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Lock, null, tint = accent, modifier = Modifier.size(56.dp))
            }
            Text(settings.title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (settings.showAppName) {
                Text(appLabel, color = accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
            Text(message, color = Color(0xFFC1CCC5), textAlign = TextAlign.Center, fontSize = 16.sp)
            Text(reason, color = Color(0xFF7F9086), textAlign = TextAlign.Center, fontSize = 12.sp)

            Button(
                onClick = onHome,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
            ) {
                Icon(Icons.Rounded.Home, null)
                Spacer(Modifier.width(8.dp))
                Text("Go home", fontWeight = FontWeight.Bold)
            }

            if (allowBreaks) {
                OutlinedButton(
                    onClick = {
                        if (!strict || !strictSettings.enabled) {
                            onBreakGranted()
                        } else {
                            when (strictSettings.unlockMethod ?: "PASSWORD") {
                                "BIOMETRIC" -> launchBiometricVerification(
                                    context,
                                    onSuccess = onBreakGranted,
                                    onError = { biometricError = it }
                                )
                                "TIMER" -> biometricError = "Breaks are unavailable while timer-based Strict Mode is active."
                                else -> showPassword = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accent)
                ) {
                    Text("Take a 5-minute break", color = Color.White)
                }
                if (biometricError.isNotBlank()) {
                    Text(biometricError, color = Color(0xFFFF8A84), fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            } else {
                Text("Breaks are disabled for this rule.", color = Color(0xFF7E8BA1), fontSize = 13.sp)
            }
        }
    }

    if (showPassword) {
        AlertDialog(
            onDismissRequest = { showPassword = false },
            title = { Text("Verify Strict Mode") },
            text = {
                Column {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it.take(64); error = false },
                        singleLine = true,
                        isError = error,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                        placeholder = { Text("Password") }
                    )
                    if (error) Text("Incorrect password", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (Prefs.checkPin(context, password)) onBreakGranted() else error = true
                }) { Text("Unlock 5 min") }
            },
            dismissButton = { TextButton(onClick = { showPassword = false }) { Text("Cancel") } }
        )
    }
}
