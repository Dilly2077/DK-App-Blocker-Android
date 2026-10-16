package com.dk.appblocker

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/** Re-read clocks on resume; never count ticks or run a UI timer while the screen is paused. */
@Composable
internal fun rememberVisibleTick(): Long {
    val lifecycle = (LocalContext.current as LifecycleOwner).lifecycle
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                tick = System.currentTimeMillis()
                delay(1000)
            }
        }
    }
    return tick
}

internal data class BlockingPermissions(val accessibility: Boolean, val usage: Boolean, val connected: Boolean = false) {
    val operational: Boolean get() = accessibility && connected
}

@Composable
internal fun rememberBlockingPermissions(tick: Long): BlockingPermissions {
    val context = LocalContext.current
    val lifecycle = (context as LifecycleOwner).lifecycle
    fun read() = BlockingPermissions(isAccessibilityEnabled(context), hasUsageAccess(context), BlockAccessibilityService.connected)
    var state by remember(context) { mutableStateOf(read()) }
    // No temporary false/default state on composition or resume. Observe grants/revocations as well.
    DisposableEffect(context, lifecycle) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { state = read() }
        }
        val listener = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) state = read() }
        context.contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, observer)
        context.contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED), false, observer)
        lifecycle.addObserver(listener)
        onDispose { lifecycle.removeObserver(listener); context.contentResolver.unregisterContentObserver(observer) }
    }
    // Also refresh AppOps, which has no public settings ContentObserver contract.
    LaunchedEffect(tick) { state = read() }
    return state
}
