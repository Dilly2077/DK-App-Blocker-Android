package com.dk.appblocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Package/window identities and rectangles only. No node, text, title, source or hierarchy access. */
class BlockAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var connected = false
            private set
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val scanLock = Mutex()
    private val tracked = TargetWindows()
    private val paneOverlay by lazy { PaneBlockOverlay(this) }
    private var targets = emptySet<String>()
    private var homes = emptySet<String>()
    private var strict = StrictSettings()
    private var generation = 0L
    private var unrelatedBarrier: Int? = null
    private var checkJob: Job? = null
    private var observing = false
    private var subscription: Pair<Set<String>?, Int>? = null
    private val lastLogged = mutableMapOf<String, Long>()
    private val overlayIds = mutableSetOf<Int>()
    internal var windowMetadataReads = 0
        private set
    internal var targetEvaluations = 0
        private set
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in setOf("plans", "strict", "focus_session", "focus_deadline", "focus_packages")) {
            // Invalidate synchronously on Main before a slower policy/usage calculation can draw.
            scope.launch { generation++; paneOverlay.clear(); scheduleCheck() }
        }
    }

    override fun onServiceConnected() {
        connected = true
        if (!observing) { Prefs.observePolicy(this, preferenceListener); observing = true }
        if (Build.VERSION.SDK_INT >= 33) runCatching { setCacheEnabled(false) }
        scope.launch {
            try {
                while (isActive) {
                    checkVisibleWindows()
                    delay(2000) // Policy/expiry tick; window metadata is read only with target candidates.
                }
            } finally { connected = false }
        }
    }

    internal suspend fun refreshMonitoring() {
        val policy = withContext(Dispatchers.IO) {
            Triple(RuleEngine.selectedPackages(this@BlockAccessibilityService), Prefs.getStrict(this@BlockAccessibilityService), homePackages(this@BlockAccessibilityService))
        }
        if (targets != policy.first || strict != policy.second || homes != policy.third) {
            generation++; paneOverlay.clear()
            targets = policy.first; strict = policy.second; homes = policy.third
            tracked.select(targets)
        }
        applySubscriptions()
    }

    private fun applySubscriptions() {
        val geometry = tracked.hasCandidates
        val packages = if (geometry) null else targets + packageName +
            if (strict.enabled && (strict.blockRecents || strict.blockSplitScreen)) homes + "com.android.systemui" else emptySet()
        val flags = if (geometry) AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS else 0
        val desired = packages to flags
        if (desired == subscription) return
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                if (geometry) AccessibilityEvent.TYPE_WINDOWS_CHANGED else 0
            packageNames = packages?.sorted()?.toTypedArray()
            this.flags = flags
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 0L // Boundary events must clear stale overlays immediately.
        }
        subscription = desired
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType !in setOf(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOWS_CHANGED)) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.packageName?.toString() != packageName) paneOverlay.clear()
        // Our own bounded surfaces generate window events too. Do not create a remove/add loop.
        if (tracked.hasCandidates && event.windowId >= 0) {
            val eventWindow = metadata().firstOrNull { it.id == event.windowId }
            if (event.windowId in overlayIds && eventWindow == null) { overlayIds.remove(event.windowId); return }
            if (eventWindow?.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
                overlayIds += event.windowId
                return
            }
            overlayIds.remove(event.windowId)
        }
        generation++
        paneOverlay.clear()
        val id = event.windowId
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString().orEmpty() // Identity only; never retain the event.
            tracked.observe(id, pkg, SystemClock.elapsedRealtime())
            unrelatedBarrier = if (pkg in targets) null else if (id >= 0) id else Int.MIN_VALUE
            // Read a class name only for system/launcher navigation hints, never an unrelated app.
            if (strict.enabled && (pkg == "com.android.systemui" || pkg in homes)) {
                val cls = event.className?.toString().orEmpty().lowercase()
                if ((strict.blockRecents && ("recentsactivity" in cls || "overviewactivity" in cls)) ||
                    (strict.blockSplitScreen && ("splitscreen" in cls || "divider" in cls))) performGlobalAction(GLOBAL_ACTION_HOME)
            }
        } else if (Build.VERSION.SDK_INT >= 28 && event.windowChanges and AccessibilityEvent.WINDOWS_CHANGE_REMOVED != 0) {
            tracked.removed(id)
            if (unrelatedBarrier == id) unrelatedBarrier = null
        }
        applySubscriptions()
        if (tracked.hasCandidates) scheduleCheck()
    }

    private fun scheduleCheck() {
        checkJob?.cancel()
        checkJob = scope.launch { delay(60); checkVisibleWindows() }
    }

    private data class Window(val id: Int, val type: Int, val focused: Boolean, val active: Boolean,
                              val layer: Int, val bounds: PaneProtection.Bounds)
    private fun metadata(): List<Window> {
        windowMetadataReads++
        return runCatching { windows }.getOrDefault(emptyList()).map { window ->
            val bounds = Rect().also(window::getBoundsInScreen)
            Window(window.id, window.type, window.isFocused, window.isActive, window.layer,
                PaneProtection.Bounds(bounds.left, bounds.top, bounds.right, bounds.bottom))
        }
    }

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
        refreshMonitoring()
        if (!tracked.hasCandidates) { paneOverlay.clear(); return@withLock }
        if (!(getSystemService(POWER_SERVICE) as PowerManager).isInteractive ||
            (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked) {
            paneOverlay.clear(); return@withLock
        }
        val snapshot = metadata()
        val apps = snapshot.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        tracked.reconcile(apps.map { it.id }.toSet(), SystemClock.elapsedRealtime())
        applySubscriptions()
        val foreground = snapshot.firstOrNull { (it.active || it.focused) &&
            it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY && it.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        if (foreground == null || foreground.type != AccessibilityWindowInfo.TYPE_APPLICATION ||
            unrelatedBarrier?.let { barrier -> snapshot.none { it.id == barrier } } == true) {
            paneOverlay.clear(); return@withLock
        }
        val candidates = apps.filter { tracked.packageFor(it.id) in targets }
        if (candidates.isEmpty()) { paneOverlay.clear(); return@withLock }
        val revision = generation
        val candidatePackages = candidates.mapNotNull { tracked.packageFor(it.id) }.toSet()
        val matches = withContext(Dispatchers.Default) {
            candidatePackages.associateWith {
                targetEvaluations++
                RuleEngine.shouldBlock(this@BlockAccessibilityService, it)
            }
        }
        if (generation != revision || metadata() != snapshot) { paneOverlay.clear(); return@withLock }
        val panes = mutableListOf<PaneBlockOverlay.Pane>()
        val occupied = mutableListOf<PaneProtection.Bounds>()
        for (window in candidates.sortedByDescending { it.layer }) {
            val pkg = tracked.packageFor(window.id) ?: continue
            val match = matches[pkg] ?: continue
            if (!match.blocked) continue
            // All unknown/other application rectangles are protected, regardless of their identity.
            val systemRegions = snapshot.filter { it.type != AccessibilityWindowInfo.TYPE_APPLICATION &&
                it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY && it.layer > window.layer }.map { it.bounds }
            val exclusions = apps.filter { tracked.packageFor(it.id) != pkg }.map { it.bounds } + systemRegions + occupied
            val regions = PaneProtection.regions(window.bounds, exclusions, safeDisplay())
            regions.forEachIndexed { index, bounds -> panes += PaneBlockOverlay.Pane("${window.id}:${index}", pkg, bounds, match.reason) }
            occupied += regions
        }
        val shown = paneOverlay.update(panes, targets, onReview = { pkg ->
            if (pkg in targets && RuleEngine.shouldBlock(this, pkg).blocked) {
                // A full activity is opened only by the user's Review action, never by an app switch.
                startActivity(Intent(this, BlockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(BlockActivity.EXTRA_PACKAGE, pkg))
            }
        }, onHome = { performGlobalAction(GLOBAL_ACTION_HOME) })
        if (panes.isNotEmpty()) {
            if (overlayIds.size > 64) overlayIds.clear()
            overlayIds += metadata().filter { it.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }.map { it.id }
        }
        shown.forEach { pkg ->
            val elapsed = SystemClock.elapsedRealtime()
            if (elapsed - (lastLogged[pkg] ?: -30_001L) >= 30_000L) {
                Prefs.addBlockEvent(this, BlockEvent(System.currentTimeMillis(), pkg, matches.getValue(pkg).reason))
                lastLogged[pkg] = elapsed
            }
        }
    }

    override fun onInterrupt() { generation++; checkJob?.cancel(); tracked.clear(); paneOverlay.clear(); applySubscriptions() }
    override fun onDestroy() {
        connected = false
        if (observing) Prefs.stopObservingPolicy(this, preferenceListener)
        scope.cancel(); tracked.clear(); paneOverlay.clear()
        super.onDestroy()
    }
}
