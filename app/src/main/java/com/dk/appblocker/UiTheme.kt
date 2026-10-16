package com.dk.appblocker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
internal val Ink = Color(0xFF020604)
internal val Ink2 = Color(0xFF07110C)
internal val Panel = Color(0xFF0E1713)
internal val Panel2 = Color(0xFF111D18)
internal val Line = Color(0xFF1B3A2B)
internal val Green = Color(0xFF35F47A)
internal val Green2 = Color(0xFF8BFFA8)
internal val Muted = Color(0xFF98A69E)
internal val Danger = Color(0xFFFF625C)
internal val Amber = Color(0xFFFFC857)

/** Shared rhythm and dimensions for every feature screen. */
internal object DKSpace {
    val screen = 20.dp
    val card = 16.dp
    val gap = 12.dp
    val compact = 8.dp
    val radius = 22.dp
    val control = 52.dp
}

@Composable
fun DKTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Green,
            secondary = Green2,
            primaryContainer = Line,
            onPrimaryContainer = Green2,
            secondaryContainer = Line,
            onSecondaryContainer = Green2,
            tertiary = Green2,
            tertiaryContainer = Panel2,
            onTertiaryContainer = Green2,
            background = Ink,
            surface = Panel,
            surfaceVariant = Panel2,
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Muted,
            error = Danger
        ),
        typography = Typography(
            headlineLarge = androidx.compose.ui.text.TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.6).sp),
            titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        ),
        content = { CompositionLocalProvider(LocalContentColor provides Color.White) { content() } }
    )
}
@Composable
internal fun ScreenBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().safeDrawingPadding().background(
            Brush.verticalGradient(listOf(Color(0xFF03100A), Ink, Color.Black), endY = 820f)
        ),
        content = content
    )
}
@Composable
internal fun GlowCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(DKSpace.radius),
        colors = CardDefaults.cardColors(containerColor = Panel2, contentColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
    ) { Column(content = content) }
}
@Composable
internal fun SettingCard(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel2, contentColor = Color.White)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 28.sp); Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 12.sp) }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

@Composable
internal fun LabeledCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(DKSpace.radius), colors = CardDefaults.cardColors(containerColor = Panel, contentColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(DKSpace.card), verticalArrangement = Arrangement.spacedBy(DKSpace.gap)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 12.sp)
            content()
        }
    }
}

@Composable
internal fun ToggleRow(title: String, value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text(title, modifier = Modifier.weight(1f)); Switch(value, onChange, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = Green)) }
}
