package com.dk.appblocker

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class StrictDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return if (Prefs.getStrict(context).enabled) {
            "Strict Mode is active. Disable Strict Mode inside DK App Blocker before removing device administrator protection."
        } else {
            "Disabling device administrator protection will allow DK App Blocker to be uninstalled normally."
        }
    }
}
