package com.dk.appblocker

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class StrictDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return if (Prefs.getStrict(context).enabled) {
            "Strict Mode is active. Removing administrator access is always allowed. You can also disable Accessibility or uninstall from Android Settings."
        } else {
            "Disabling device administrator protection will allow DK App Blocker to be uninstalled normally."
        }
    }
}
