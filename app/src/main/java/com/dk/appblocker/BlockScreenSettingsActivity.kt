package com.dk.appblocker

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class BlockScreenSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DKTheme {
                BlockScreenSettingsScreen(onBack = { finish() })
            }
        }
    }
}

private fun parseHexColor(value: String, fallback: Int): Color =
    runCatching { Color(AndroidColor.parseColor(value)) }.getOrElse { Color(fallback) }

@Composable
private fun BlockScreenSettingsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val saved = remember { Prefs.getBlockScreen(context) }
    var title by remember { mutableStateOf(saved.title) }
    var message by remember { mutableStateOf(saved.message) }
    var backgroundHex by remember { mutableStateOf(saved.backgroundHex) }
    var accentHex by remember { mutableStateOf(saved.accentHex) }
    var showAppName by remember { mutableStateOf(saved.showAppName) }
    var savedNotice by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    val bg = parseHexColor(backgroundHex, 0xFF020604.toInt())
    val accent = parseHexColor(accentHex, 0xFF35F47A.toInt())
    val backgrounds = listOf("#020604", "#07110C", "#111111", "#071A12", "#101820", "#1A0C0C")
    val accents = listOf("#35F47A", "#8BFFA8", "#5CE1E6", "#FFD166", "#B388FF", "#FF6B6B")

    Scaffold(containerColor = Color.Black) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Text("Blocked screen", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = bg),
                border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .45f))
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🔒", fontSize = 40.sp)
                    Text(title.ifBlank { "Blocked" }, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Color.White)
                    if (showAppName) Text("Example App", color = accent, fontWeight = FontWeight.SemiBold)
                    Text(
                        message.ifBlank { "Stay focused. {app} is blocked right now." }
                            .replace("{app}", "Example App")
                            .replace("{reason}", "Daily limit reached"),
                        color = Color(0xFFC4CEC8),
                        fontSize = 14.sp
                    )
                }
            }

            Text("Background", fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(backgrounds) { hex ->
                    val color = parseHexColor(hex, 0xFF020604.toInt())
                    Box(
                        Modifier.size(42.dp).background(color, CircleShape).clickable { backgroundHex = hex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (backgroundHex.equals(hex, true)) Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            OutlinedTextField(
                value = backgroundHex,
                onValueChange = { backgroundHex = it.take(9) },
                label = { Text("Background hex") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Accent", fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(accents) { hex ->
                    val color = parseHexColor(hex, 0xFF35F47A.toInt())
                    Box(
                        Modifier.size(42.dp).background(color, CircleShape).clickable { accentHex = hex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (accentHex.equals(hex, true)) Text("✓", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(40) },
                label = { Text("Heading") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = message,
                onValueChange = { message = it.take(220) },
                label = { Text("Message") },
                supportingText = { Text("Use {app} for the app name and {reason} for the blocking reason.") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show blocked app name", modifier = Modifier.weight(1f))
                Switch(checked = showAppName, onCheckedChange = { showAppName = it })
            }

            Button(
                onClick = {
                    Prefs.saveBlockScreen(
                        context,
                        BlockScreenSettings(
                            title = title.ifBlank { "Blocked" },
                            message = message.ifBlank { "Stay focused. {app} is blocked right now." },
                            backgroundHex = backgroundHex.ifBlank { "#020604" },
                            accentHex = accentHex.ifBlank { "#35F47A" },
                            showAppName = showAppName
                        )
                    )
                    savedNotice = true
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF35F47A), contentColor = Color.Black),
                shape = RoundedCornerShape(18.dp)
            ) { Text(if (savedNotice) "Saved ✓" else "Save blocked screen", fontWeight = FontWeight.Bold) }
        }
    }
}
