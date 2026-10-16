package com.dk.appblocker

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DKTheme { AppRoot() } }
    }
    override fun onResume() {
        super.onResume()
        GeofenceManager.sync(this)
    }
}
