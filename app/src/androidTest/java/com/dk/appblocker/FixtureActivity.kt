package com.dk.appblocker

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Separate test APK acts as a real, external distraction app. Never shipped in the product APK. */
class FixtureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("keyboard", false)) {
            val input = android.widget.EditText(this).apply { setText("Fixture distraction"); textSize = 24f }
            setContentView(input)
            input.requestFocus()
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        } else setContentView(TextView(this).apply { text = "Fixture distraction"; textSize = 24f })
    }
}
