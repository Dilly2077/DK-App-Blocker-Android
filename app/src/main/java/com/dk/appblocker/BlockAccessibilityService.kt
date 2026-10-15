package com.dk.appblocker

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.os.SystemClock
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.*

class BlockAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var checkJob: Job? = null
    private var lastPackage: String? = null
    private var lastLaunchAt = -1000L
    private val lastLogged = mutableMapOf<String, Long>()

    override fun onServiceConnected() {
        scope.launch {
            while (isActive) {
                checkVisibleWindows()
                delay(1000)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event?.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        val strict = Prefs.getStrict(this)
        val pkg = event?.packageName?.toString().orEmpty()
        val cls = event?.className?.toString().orEmpty().lowercase()
        // OEM-specific hints are best effort. Settings, permission controllers and the shade stay reachable.
        if (strict.enabled && (pkg == "com.android.systemui" || pkg in homePackages(this)) &&
            ((strict.blockRecents && ("recentsactivity" in cls || "overviewactivity" in cls)) ||
                (strict.blockSplitScreen && ("splitscreen" in cls || "divider" in cls)))) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
        checkJob?.cancel()
        checkJob = scope.launch { delay(100); checkVisibleWindows() }
    }

    private suspend fun checkVisibleWindows() {
        if (!(getSystemService(POWER_SERVICE) as PowerManager).isInteractive) return
        if ((getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked) return
        val current = runCatching { windows }.getOrDefault(emptyList())
        val foreground = current.firstOrNull { it.isActive || it.isFocused } ?: return
        val activePackage = foreground.root?.packageName?.toString() ?: return
        if (isRecoveryPackage(this, activePackage) ||
            foreground.type != AccessibilityWindowInfo.TYPE_APPLICATION) return
        val candidates = current.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { it.root?.packageName?.toString() }.distinct()
        for (pkg in candidates) {
            val match = withContext(Dispatchers.Default) { RuleEngine.shouldBlock(this@BlockAccessibilityService, pkg) }
            val latest = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
            if (latest != activePackage) return
            if (match.blocked) { showBlocked(pkg, match); return }
        }
        lastPackage = null
    }

    private fun showBlocked(pkg: String, match: RuleEngine.Match) {
        val elapsed = SystemClock.elapsedRealtime()
        if (lastPackage == pkg && elapsed - lastLaunchAt < 900L) return
        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
        }
        if (runCatching { startActivity(intent) }.isFailure) return
        lastPackage = pkg
        lastLaunchAt = elapsed
        if (elapsed - (lastLogged[pkg] ?: -30_001L) >= 30_000L) {
            Prefs.addBlockEvent(this, BlockEvent(System.currentTimeMillis(), pkg, match.reason))
            lastLogged[pkg] = elapsed
        }
    }

    override fun onInterrupt() { checkJob?.cancel() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
