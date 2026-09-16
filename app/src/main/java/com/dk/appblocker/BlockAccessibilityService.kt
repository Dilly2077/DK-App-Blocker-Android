package com.dk.appblocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class BlockAccessibilityService : AccessibilityService() {
    private var lastPackage: String? = null
    private var lastLaunchAt: Long = 0L
    private val lastLogged = mutableMapOf<String, Long>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val strict = Prefs.getStrict(this)
        if (strict.enabled) {
            val cls = event.className?.toString().orEmpty().lowercase()

            if (pkg == "com.android.systemui") {
                val isRecents = "recent" in cls || "overview" in cls
                val isSplit = "split" in cls || "multiwindow" in cls || "divider" in cls
                if ((strict.blockRecents && isRecents) || (strict.blockSplitScreen && isSplit)) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
                return
            }

            if (strict.blockDeviceSettings && pkg == "com.android.settings") {
                showBlocked(pkg, "Strict Mode · device settings are locked", true, false)
                return
            }

            val installerPackages = setOf(
                "com.google.android.packageinstaller",
                "com.android.packageinstaller",
                "com.google.android.permissioncontroller",
                "com.android.permissioncontroller"
            )
            if (strict.preventUninstall && pkg in installerPackages) {
                showBlocked(pkg, "Strict Mode · app uninstalling is locked", true, false)
                return
            }
        } else if (pkg == "com.android.systemui") {
            return
        }

        val match = RuleEngine.shouldBlock(this, pkg)
        if (!match.blocked) {
            lastPackage = pkg
            return
        }
        showBlocked(pkg, match.reason, match.plan?.strict == true, match.plan?.allowBreaks != false)
    }

    private fun showBlocked(pkg: String, reason: String, strict: Boolean, allowBreaks: Boolean) {
        val now = System.currentTimeMillis()
        if (lastPackage == pkg && now - lastLaunchAt < 900L) return
        lastPackage = pkg
        lastLaunchAt = now

        val previousLog = lastLogged[pkg] ?: 0L
        if (now - previousLog > 30_000L) {
            Prefs.addBlockEvent(this, BlockEvent(now, pkg, reason))
            lastLogged[pkg] = now
        }

        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
            putExtra(BlockActivity.EXTRA_REASON, reason)
            putExtra(BlockActivity.EXTRA_STRICT, strict)
            putExtra(BlockActivity.EXTRA_ALLOW_BREAKS, allowBreaks)
        }
        startActivity(intent)
    }

    override fun onInterrupt() = Unit
}
