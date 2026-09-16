package com.dk.appblocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class BlockAccessibilityService : AccessibilityService() {
    private var lastPackage: String? = null
    private var lastLaunchAt: Long = 0L
    private val lastLogged = mutableMapOf<String, Long>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return

        val match = RuleEngine.shouldBlock(this, pkg)
        if (!match.blocked) {
            lastPackage = pkg
            return
        }

        val now = System.currentTimeMillis()
        if (lastPackage == pkg && now - lastLaunchAt < 900L) return
        lastPackage = pkg
        lastLaunchAt = now

        val previousLog = lastLogged[pkg] ?: 0L
        if (now - previousLog > 30_000L) {
            Prefs.addBlockEvent(this, BlockEvent(now, pkg, match.reason))
            lastLogged[pkg] = now
        }

        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
            putExtra(BlockActivity.EXTRA_REASON, match.reason)
            putExtra(BlockActivity.EXTRA_STRICT, match.plan?.strict == true)
            putExtra(BlockActivity.EXTRA_ALLOW_BREAKS, match.plan?.allowBreaks != false)
        }
        startActivity(intent)
    }

    override fun onInterrupt() = Unit
}
