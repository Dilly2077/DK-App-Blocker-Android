package com.dk.appblocker

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Prefs.clearGeofences(context)
            Prefs.setGeofenceFingerprint(context, null)
            Prefs.setGeofenceStatus(context, "Geofence error ${event.errorCode}; reopen the app to retry")
            return
        }
        if (!GeofenceManager.hasPermissions(context)) { Prefs.clearGeofences(context); return }
        val active = event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
            event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL
        val exiting = event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT
        event.triggeringGeofences?.forEach { geofence ->
            if (Prefs.getPlans(context).none { it.id == geofence.requestId && it.enabled && it.triggerType == TriggerType.LOCATION }) return@forEach
            if (active) Prefs.setActiveGeofence(context, geofence.requestId, true)
            if (exiting) Prefs.setActiveGeofence(context, geofence.requestId, false)
        }
    }
}

object GeofenceManager {
    private var syncing = false
    private var retry = false

    fun hasPermissions(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            (Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED)
    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            901,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    fun sync(context: Context, plans: List<BlockPlan> = Prefs.getPlans(context), onComplete: () -> Unit = {}) {
        if (syncing) { retry = true; onComplete(); return }
        val locationPlans = plans.filter {
            it.enabled &&
                it.triggerType == TriggerType.LOCATION &&
                (it.locationConfigured || it.latitude != 0.0 || it.longitude != 0.0) &&
                it.latitude in -90.0..90.0 &&
                it.longitude in -180.0..180.0 && it.radiusMeters.isFinite()
        }
        val fingerprint = com.google.gson.Gson().toJson(locationPlans) + hasPermissions(context) + Prefs.boot(context)
        if (fingerprint == Prefs.geofenceFingerprint(context)) { onComplete(); return }
        Prefs.clearGeofences(context)
        Prefs.setGeofenceFingerprint(context, null)
        if (!hasPermissions(context)) {
            Prefs.setGeofenceStatus(context, "Grant precise and background location in Android Settings, then reopen the app")
            onComplete()
            return
        }
        if (locationPlans.size > 100) {
            Prefs.setGeofenceStatus(context, "Android supports up to 100 location rules; disable extra rules")
            onComplete()
            return
        }
        syncing = true
        fun complete(error: Exception? = null) {
            syncing = false
            Prefs.setGeofenceFingerprint(context, if (error == null) fingerprint else null)
            Prefs.setGeofenceStatus(context, if (error == null) "${locationPlans.size} location rules registered" else "Registration failed: ${error.javaClass.simpleName}. Check permissions and Google Play services, then reopen the app.")
            onComplete()
            if (retry) { retry = false; sync(context.applicationContext) }
        }
        val client: GeofencingClient = LocationServices.getGeofencingClient(context)
        val pi = pendingIntent(context)
        client.removeGeofences(pi).addOnCompleteListener { removal ->
            if (!removal.isSuccessful) { complete(removal.exception ?: IllegalStateException("Removal failed")); return@addOnCompleteListener }
            if (locationPlans.isEmpty()) { complete(); return@addOnCompleteListener }
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
            try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    complete(SecurityException("Location permission revoked")); return@addOnCompleteListener
                }
                client.addGeofences(request, pi).addOnCompleteListener { result ->
                    complete(if (result.isSuccessful) null else result.exception ?: IllegalStateException("Registration failed"))
                }
            } catch (error: SecurityException) { complete(error) }
        }
    }
}
