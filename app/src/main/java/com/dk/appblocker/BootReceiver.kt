package com.dk.appblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Prefs.clearGeofences(context)
            Prefs.setGeofenceFingerprint(context, null)
            Prefs.getStrict(context) // Expire temporary locks safely after reboot.
            val pending = goAsync()
            GeofenceManager.sync(context.applicationContext, onComplete = { pending.finish() })
        }
    }
}
