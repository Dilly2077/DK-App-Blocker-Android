package com.dk.appblocker

import android.content.Context
import java.util.UUID

enum class PolicyWrite { SAVED, LOCKED, STORAGE_ERROR }
enum class RuleAction { TOGGLE, DUPLICATE, DELETE }

/** UI calls on Dispatchers.IO; reconciliation starts only after the policy is on disk. */
class RuleRepository(private val context: Context, private val sync: () -> Unit = { GeofenceManager.sync(context) }) {
    fun save(plan: BlockPlan): PolicyWrite = commit { plans ->
        if (plans.any { it.id == plan.id }) plans.map { if (it.id == plan.id) plan else it } else plans + plan
    }
    fun perform(id: String, action: RuleAction): PolicyWrite = commit { plans ->
        val existing = plans.firstOrNull { it.id == id } ?: return@commit plans
        when (action) {
            RuleAction.TOGGLE -> plans.map { if (it.id == id) it.copy(enabled = !it.enabled) else it }
            RuleAction.DUPLICATE -> plans + existing.copy(id = UUID.randomUUID().toString(), name = "${existing.name} (copy)")
            RuleAction.DELETE -> plans.filterNot { it.id == id }
        }
    }
    private fun commit(change: (List<BlockPlan>) -> List<BlockPlan>): PolicyWrite {
        val result = Prefs.changePlans(context, change)
        if (result == PolicyWrite.SAVED) sync()
        return result
    }
}
