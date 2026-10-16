package com.dk.appblocker

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(64, 64).asImageBitmap() }.getOrNull()
        }
    }
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Line), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) } ?: Text("🌿")
    }
}

@Composable
internal fun AppIconStrip(packages: Set<String>, maxIcons: Int = 5) {
    Row(horizontalArrangement = Arrangement.spacedBy(DKSpace.compact), verticalAlignment = Alignment.CenterVertically) {
        packages.sorted().take(maxIcons).forEach { AppIcon(it) }
        if (packages.size > maxIcons) Text("+${packages.size - maxIcons}", color = Muted)
    }
}
