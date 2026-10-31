package com.dk.compatfixture

import android.app.Activity
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Representative UI only. No real account, bank SDK, network permission or stored content. */
class FixtureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 48, 24, 24)
            addView(TextView(this@FixtureActivity).apply {
                text = getString(if (packageName.endsWith("social")) R.string.social_title else R.string.finance_title)
                textSize = 24f
            })
            addView(EditText(this@FixtureActivity).apply { hint = getString(R.string.synthetic_hint); setText(R.string.synthetic_value) })
        })
    }
}
