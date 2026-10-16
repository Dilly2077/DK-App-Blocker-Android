package com.dk.appblocker

import java.security.MessageDigest

data class GeofenceSpec(val id: String, val latitude: Double, val longitude: Double, val radius: Float) {
    val registrationId: String get() {
        val geometry = "$latitude|$longitude|$radius"
        val digest = MessageDigest.getInstance("SHA-256").digest(geometry.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
        return "$id@$digest"
    }
    companion object {
        fun from(plan: BlockPlan) = GeofenceSpec(plan.id, plan.latitude, plan.longitude, plan.radiusMeters.coerceAtLeast(100f))
        fun desired(plans: List<BlockPlan>): List<GeofenceSpec> = plans.filter {
            it.enabled && it.triggerType == TriggerType.LOCATION &&
                (it.locationConfigured || it.latitude != 0.0 || it.longitude != 0.0) &&
                it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 && it.radiusMeters.isFinite()
        }.map(::from)
    }
}

interface GeofenceBackend {
    suspend fun add(specs: List<GeofenceSpec>)
    suspend fun remove(ids: List<String>)
}

/** Never remove/re-register unchanged regions. New geometry gets a new event ID, rejecting late events. */
object GeofenceReconciliation {
    suspend fun reconcile(previous: List<GeofenceSpec>, desired: List<GeofenceSpec>, obsolete: Set<String>, backend: GeofenceBackend) {
        require(desired.size <= 100)
        val desiredIds = desired.map { it.registrationId }.toSet()
        val remove = (previous.map { it.registrationId }.toSet() + obsolete) - desiredIds
        val add = desired.filter { it !in previous }
        // At capacity Android cannot register old and replacement regions simultaneously. Only obsolete
        // regions are removed first; unchanged occupancy/registrations are preserved in either order.
        val removeFirst = previous.size + add.size + obsolete.count { it !in previous.map { spec -> spec.registrationId } } > 100
        if (removeFirst && remove.isNotEmpty()) backend.remove(remove.toList())
        if (add.isNotEmpty()) backend.add(add)
        if (!removeFirst && remove.isNotEmpty()) backend.remove(remove.toList())
    }
}
