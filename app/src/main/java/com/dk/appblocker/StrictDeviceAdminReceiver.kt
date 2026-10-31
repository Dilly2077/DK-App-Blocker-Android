package com.dk.appblocker

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class StrictDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return if (Prefs.getStrict(context).enabled) {
            "Strict Mode is active. Removing administrator access is always allowed. You can also disable Accessibility or uninstall from Android Settings."
        } else {
            "Administrator access is optional. Removing it leaves your rules and Strict Mode unchanged; uninstalling remains available in Android Settings."
        }
    }
}
