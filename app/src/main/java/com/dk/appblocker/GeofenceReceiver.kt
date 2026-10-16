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
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (event.hasError() || !GeofenceManager.hasPermissions(context)) {
                    Prefs.invalidateGeofenceRegistrations(context)
                    Prefs.setGeofenceStatus(context, "Location events unavailable; reopen the app to retry")
                    return@launch
                }
                val entering = event.geofenceTransition in setOf(Geofence.GEOFENCE_TRANSITION_ENTER, Geofence.GEOFENCE_TRANSITION_DWELL)
                val exiting = event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT
                val specs = GeofenceSpec.desired(Prefs.getPlans(context))
                event.triggeringGeofences?.forEach { fence ->
                    val spec = specs.firstOrNull { it.registrationId == fence.requestId } ?: return@forEach
                    if (entering || exiting) Prefs.setActiveGeofence(context, spec.id, entering, spec.registrationId)
                }
            } finally { pending.finish() }
        }
    }
}

object GeofenceManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncLock = Mutex()
    fun hasPermissions(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            (Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED)

    private suspend fun <T> Task<T>.awaitLocal(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Location operation failed"))
        }
    }

    fun sync(context: Context, plans: List<BlockPlan> = Prefs.getPlans(context), onComplete: () -> Unit = {}) {
        // Re-read durable plans in the serialised job; a caller's snapshot may already be stale.
        @Suppress("UNUSED_VARIABLE") val callerSnapshot = plans
        val app = context.applicationContext
        scope.launch {
            try { syncLock.withLock { reconcile(app) } }
            catch (error: Exception) {
                if (!hasPermissions(app)) Prefs.clearGeofences(app)
                Prefs.setGeofenceFingerprint(app, null)
                Prefs.setGeofenceStatus(app, "Location sync unavailable; reopen to retry")
            }
            finally { withContext(Dispatchers.Main) { onComplete() } }
        }
    }

    private suspend fun reconcile(context: Context) {
        val desired = GeofenceSpec.desired(Prefs.getPlans(context))
        val knownBoot = Prefs.geofenceRegistrationBoot(context)
        val bootChanged = knownBoot >= 0 && knownBoot != Prefs.boot(context)
        val previous = if (bootChanged) emptyList() else Prefs.registeredGeofences(context)
        val allowed = hasPermissions(context)
        if (!allowed || bootChanged) Prefs.clearGeofences(context)
        else Prefs.retainActiveGeofences(context, desired.filter { knownBoot < 0 || it in previous }.map { it.id }.toSet())
        if (desired.size > 100) {
            Prefs.setGeofenceStatus(context, "Android supports up to 100 location rules; disable extra rules")
            return
        }
        val fingerprint = com.google.gson.Gson().toJson(desired) + allowed + Prefs.boot(context)
        if (fingerprint == Prefs.geofenceFingerprint(context) && Prefs.pendingGeofenceRemovals(context).isEmpty()) return
        val client = LocationServices.getGeofencingClient(context)
        val pi = PendingIntent.getBroadcast(context, 901, Intent(context, GeofenceReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        val backend = object : GeofenceBackend {
            override suspend fun remove(ids: List<String>) { if (ids.isNotEmpty()) client.removeGeofences(ids).awaitLocal() }
            override suspend fun add(specs: List<GeofenceSpec>) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    !hasPermissions(context)) throw SecurityException("Location permission revoked")
                val fences = specs.map {
                    Geofence.Builder().setRequestId(it.registrationId).setCircularRegion(it.latitude, it.longitude, it.radius)
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT or Geofence.GEOFENCE_TRANSITION_DWELL)
                        .setLoiteringDelay(60_000).setNotificationResponsiveness(30_000).build()
                }
                client.addGeofences(GeofencingRequest.Builder().setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER).addGeofences(fences).build(), pi).awaitLocal()
            }
        }
        try {
            Prefs.setGeofenceStatus(context, "Updating location registrations…")
            val legacy = if (previous.isEmpty()) Prefs.getPlans(context).filter { it.triggerType == TriggerType.LOCATION }.map { it.id }.toSet() else emptySet()
            withTimeout(30_000L) {
                GeofenceReconciliation.reconcile(previous, if (allowed) desired else emptyList(),
                    Prefs.pendingGeofenceRemovals(context) + legacy + if (!allowed) Prefs.registeredGeofences(context).map { it.registrationId } else emptyList(), backend)
            }
            if (!allowed) {
                Prefs.finishGeofenceSync(context, emptyList())
                Prefs.setGeofenceFingerprint(context, null)
                Prefs.setGeofenceStatus(context, "Grant precise and background location for location rules")
            } else if (GeofenceSpec.desired(Prefs.getPlans(context)) == desired && hasPermissions(context)) {
                if (!Prefs.finishGeofenceSync(context, desired)) throw IllegalStateException("Could not persist location registrations")
                Prefs.setGeofenceFingerprint(context, fingerprint)
                Prefs.setGeofenceStatus(context, "${desired.size} location rules registered")
            } else {
                Prefs.setGeofenceFingerprint(context, null)
                sync(context)
            }
        } catch (error: Exception) {
            if (!hasPermissions(context)) Prefs.clearGeofences(context)
            Prefs.setGeofenceFingerprint(context, null)
            Prefs.setGeofenceStatus(context, "Location sync failed; unchanged regions retained. Reopen to retry.")
        }
    }
}
