package com.dk.appblocker

/** Positive selection policy; financial-package lists are deliberately unnecessary. */
object MonitoringPolicy {
    fun targets(plans: List<BlockPlan>, focusPackages: Set<String>, recovery: Set<String>): Set<String> =
        (plans.filter { it.enabled }.flatMap { it.packages } + focusPackages).filter { it.isNotBlank() && it !in recovery }.toSet()
}

/** Only selected package/window IDs, in RAM. Never stores an unrelated package or any UI content. */
class TargetWindows {
    private data class Identity(val pkg: String, val observedAt: Long)
    private val identities = linkedMapOf<Int, Identity>()
    private var selected = emptySet<String>()
    val hasCandidates get() = identities.isNotEmpty()
    fun select(packages: Set<String>) {
        selected = packages.toSet()
        identities.entries.removeAll { it.value.pkg !in selected }
    }
    fun observe(windowId: Int, packageName: String, now: Long) {
        if (windowId < 0) return
        if (packageName !in selected) { identities.remove(windowId); return }
        if (identities.size >= 64 && windowId !in identities) identities.remove(identities.keys.first())
        identities[windowId] = Identity(packageName, now)
    }
    fun removed(windowId: Int) { identities.remove(windowId) }
    fun reconcile(visibleIds: Set<Int>, now: Long) {
        identities.entries.removeAll { it.key !in visibleIds && now - it.value.observedAt >= 2000 }
    }
    fun packageFor(windowId: Int): String? = identities[windowId]?.pkg
    fun clear() { identities.clear() }
}
