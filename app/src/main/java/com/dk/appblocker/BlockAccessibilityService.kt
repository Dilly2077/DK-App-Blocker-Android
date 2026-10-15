package com.dk.appblocker

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.os.SystemClock
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.graphics.Rect
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.*

class BlockAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var checkJob: Job? = null
    private var lastPackage: String? = null
    private var lastLaunchAt = -1000L
    private val lastLogged = mutableMapOf<String, Long>()
    private val scanLock = Mutex()
    private val paneOverlay by lazy { PaneBlockOverlay(this) }
    private data class AppWindow(val id: Int, val pkg: String, val bounds: PaneProtection.Bounds)

    override fun onServiceConnected() {
        scope.launch {
            while (isActive) {
                checkVisibleWindows()
                delay(2000) // Window events handle switches; shared usage samples are refreshed at most every five seconds.
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

    private fun appWindows(source: List<AccessibilityWindowInfo>): List<AppWindow> = source
        .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.mapNotNull { window -> runCatching {
            val pkg = window.root?.packageName?.toString() ?: return@runCatching null
            val bounds = Rect().also(window::getBoundsInScreen)
            AppWindow(window.id, pkg, PaneProtection.Bounds(bounds.left, bounds.top, bounds.right, bounds.bottom))
        }.getOrNull() }

    private fun safeDisplay(): PaneProtection.Bounds {
        val manager = getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = manager.currentWindowMetrics
            val bars = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return PaneProtection.Bounds(metrics.bounds.left + bars.left, metrics.bounds.top + bars.top,
                metrics.bounds.right - bars.right, metrics.bounds.bottom - bars.bottom)
        }
        val size = android.graphics.Point().also { manager.defaultDisplay.getRealSize(it) }
        @android.annotation.SuppressLint("DiscouragedApi") // System-bar dimensions have no public resource ID before WindowMetrics (API 30).
        fun dimension(name: String): Int = resources.getIdentifier(name, "dimen", "android").let {
            if (it == 0) 0 else resources.getDimensionPixelSize(it)
        }
        return PaneProtection.Bounds(0, dimension("status_bar_height"), size.x, size.y - dimension("navigation_bar_height"))
    }

    internal suspend fun checkVisibleWindows() = scanLock.withLock {
        if (!(getSystemService(POWER_SERVICE) as PowerManager).isInteractive ||
            (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked) {
            paneOverlay.clear(); return@withLock
        }
        val current = runCatching { windows }.getOrDefault(emptyList())
        val foreground = current.firstOrNull { (it.isActive || it.isFocused) &&
            it.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD && it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
        if (foreground == null || foreground.type != AccessibilityWindowInfo.TYPE_APPLICATION) {
            paneOverlay.clear(); return@withLock // Notification shade, keyguard and system controls stay usable.
        }
        val apps = appWindows(current)
        val matches = withContext(Dispatchers.Default) {
            apps.map { it.pkg }.distinct().associateWith { RuleEngine.shouldBlock(this@BlockAccessibilityService, it) }
        }
        if (appWindows(runCatching { windows }.getOrDefault(emptyList())) != apps) {
            paneOverlay.clear(); return@withLock // Never retain an overlay at stale coordinates after a resize/switch.
        }
        val blocked = apps.filter { matches[it.pkg]?.blocked == true }
        if (blocked.isEmpty()) { paneOverlay.clear(); lastPackage = null; return@withLock }
        val recovery = apps.filter { isRecoveryPackage(this, it.pkg) }.map { it.bounds }
        if (apps.map { it.pkg }.distinct().size > 1 || recovery.isNotEmpty()) {
            val panes = blocked.flatMap { app ->
                PaneProtection.regions(app.bounds, recovery, safeDisplay()).mapIndexed { index, region ->
                    PaneBlockOverlay.Pane("${app.id}:$index", app.pkg, region, matches.getValue(app.pkg).reason)
                }
            }
            paneOverlay.update(panes, onReview = { pkg -> showBlocked(pkg, RuleEngine.shouldBlock(this, pkg)) },
                onHome = { performGlobalAction(GLOBAL_ACTION_HOME) })
            panes.map { it.pkg }.distinct().forEach { logAttempt(it, matches.getValue(it).reason) }
        } else {
            paneOverlay.clear()
            val app = blocked.first()
            showBlocked(app.pkg, matches.getValue(app.pkg))
        }
    }

    private fun showBlocked(pkg: String, match: RuleEngine.Match) {
        if (!match.blocked) return
        val elapsed = SystemClock.elapsedRealtime()
        if (lastPackage == pkg && elapsed - lastLaunchAt < 900L) return
        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
        }
        if (runCatching { startActivity(intent) }.isFailure) return
        lastPackage = pkg
        lastLaunchAt = elapsed
        logAttempt(pkg, match.reason)
    }

    private fun logAttempt(pkg: String, reason: String) {
        val elapsed = SystemClock.elapsedRealtime()
        if (elapsed - (lastLogged[pkg] ?: -30_001L) >= 30_000L) {
            Prefs.addBlockEvent(this, BlockEvent(System.currentTimeMillis(), pkg, reason))
            lastLogged[pkg] = elapsed
        }
    }

    override fun onInterrupt() { checkJob?.cancel(); paneOverlay.clear() }
    override fun onDestroy() { scope.cancel(); paneOverlay.clear(); super.onDestroy() }
}
