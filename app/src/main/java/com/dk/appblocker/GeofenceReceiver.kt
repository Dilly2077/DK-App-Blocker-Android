package com.dk.appblocker

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val active = event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
            event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL
        val exiting = event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT
        event.triggeringGeofences?.forEach { geofence ->
            if (active) Prefs.setActiveGeofence(context, geofence.requestId, true)
            if (exiting) Prefs.setActiveGeofence(context, geofence.requestId, false)
        }
    }
}

object GeofenceManager {
    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            901,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    fun sync(context: Context, plans: List<BlockPlan> = Prefs.getPlans(context)) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val locationPlans = plans.filter {
            it.enabled &&
                it.triggerType == TriggerType.LOCATION &&
                !(it.latitude == 0.0 && it.longitude == 0.0) &&
                it.latitude in -90.0..90.0 &&
                it.longitude in -180.0..180.0
        }
        val client: GeofencingClient = LocationServices.getGeofencingClient(context)
        val pi = pendingIntent(context)
        client.removeGeofences(pi).addOnCompleteListener {
            if (locationPlans.isEmpty()) return@addOnCompleteListener
            val geofences = locationPlans.map { plan ->
                Geofence.Builder()
                    .setRequestId(plan.id)
                    .setCircularRegion(plan.latitude, plan.longitude, plan.radiusMeters.coerceAtLeast(100f))
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(
                        Geofence.GEOFENCE_TRANSITION_ENTER or
                            Geofence.GEOFENCE_TRANSITION_EXIT or
                            Geofence.GEOFENCE_TRANSITION_DWELL
                    )
                    .setLoiteringDelay(60_000)
                    .setNotificationResponsiveness(30_000)
                    .build()
            }
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofences)
                .build()
            runCatching { client.addGeofences(request, pi) }
        }
    }
}
