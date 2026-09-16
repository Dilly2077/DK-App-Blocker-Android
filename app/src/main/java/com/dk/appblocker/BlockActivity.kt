package com.dk.appblocker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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

    private fun render() {
        val blockedPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "Blocked by a rule"
        val strict = intent.getBooleanExtra(EXTRA_STRICT, false)
        val allowBreaks = intent.getBooleanExtra(EXTRA_ALLOW_BREAKS, true)
        val label = runCatching {
            val info = packageManager.getApplicationInfo(blockedPackage, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(blockedPackage)

        setContent {
            DKTheme {
                BlockedScreen(
                    appLabel = label,
                    reason = reason,
                    strict = strict,
                    allowBreaks = allowBreaks,
                    onHome = {
                        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    },
                    onBreakGranted = {
                        Prefs.allowPackageUntil(this, blockedPackage, System.currentTimeMillis() + 5 * 60_000L)
                        finish()
                    }
                )
            }
        }
    }
}

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
    var showPin by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val strictSettings = remember { Prefs.getStrict(context) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF06142E), Color(0xFF020817), Color.Black)
                )
            )
            .padding(28.dp)
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                Modifier.size(120.dp).background(Color(0xFF102A52), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Lock, null, tint = Color(0xFF55B2FF), modifier = Modifier.size(56.dp))
            }
            Text("Blocked", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(appLabel, color = Color(0xFFBFDFFF), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(reason, color = Color(0xFF9AA8BE), textAlign = TextAlign.Center, fontSize = 16.sp)

            Button(
                onClick = onHome,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Rounded.Home, null)
                Spacer(Modifier.width(8.dp))
                Text("Go home")
            }

            if (allowBreaks) {
                OutlinedButton(
                    onClick = {
                        if (strict && strictSettings.enabled && strictSettings.pinHash.isNotBlank()) showPin = true else onBreakGranted()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Take a 5-minute break")
                }
            } else {
                Text("Breaks are disabled for this strict rule.", color = Color(0xFF7E8BA1), fontSize = 13.sp)
            }
        }
    }

    if (showPin) {
        AlertDialog(
            onDismissRequest = { showPin = false },
            title = { Text("Enter Strict Mode PIN") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(8); error = false },
                        singleLine = true,
                        isError = error,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    if (error) Text("Incorrect PIN", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (Prefs.checkPin(context, pin)) onBreakGranted() else error = true
                }) { Text("Unlock 5 min") }
            },
            dismissButton = { TextButton(onClick = { showPin = false }) { Text("Cancel") } }
        )
    }
}
