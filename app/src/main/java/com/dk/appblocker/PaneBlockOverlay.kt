package com.dk.appblocker

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Opaque, touch-consuming surfaces bounded to blocked panes; never takes keyboard focus. */
class PaneBlockOverlay(private val service: AccessibilityService) {
    data class Pane(val key: String, val pkg: String, val bounds: PaneProtection.Bounds, val reason: String)
    private val manager = service.getSystemService(WindowManager::class.java)
    private val views = mutableMapOf<String, Pair<View, Pane>>()

    fun update(panes: List<Pane>, onReview: (String) -> Unit, onHome: () -> Unit) {
        val appearance = Prefs.getBlockScreen(service)
        val appearanceKey = appearance.toString()
        val wanted = panes.associateBy { "${it.key}:$appearanceKey" }
        (views.keys - wanted.keys).forEach(::remove)
        wanted.forEach { (key, pane) ->
            if (views[key]?.second == pane) return@forEach
            remove(key)
            val background = runCatching { Color.parseColor(appearance.backgroundHex) }.getOrDefault(Color.BLACK)
            val accent = runCatching { Color.parseColor(appearance.accentHex) }.getOrDefault(0xFF35F47A.toInt())
            val column = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(dp(12), dp(12), dp(12), dp(12)); setBackgroundColor(background or 0xFF000000.toInt())
                isClickable = true
            }
            fun label(text: String, size: Float, color: Int) = TextView(service).apply {
                this.text = text; textSize = size; setTextColor(color); gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(6))
            }
            column.addView(label(appearance.title, 22f, accent))
            val appLabel = runCatching {
                service.packageManager.getApplicationLabel(service.packageManager.getApplicationInfo(pane.pkg, 0)).toString()
            }.getOrDefault(pane.pkg)
            if (appearance.showAppName) column.addView(label(appLabel, 16f, accent))
            column.addView(label(appearance.message.replace("{app}", appLabel).replace("{reason}", pane.reason), 14f, Color.LTGRAY))
            column.addView(label(pane.reason, 13f, Color.WHITE))
            column.addView(Button(service).apply {
                setText(R.string.review_block); contentDescription = service.getString(R.string.review_blocked_pane)
                backgroundTintList = ColorStateList.valueOf(accent); setTextColor(Color.BLACK)
                setOnClickListener { onReview(pane.pkg) }
            })
            column.addView(Button(service).apply {
                setText(R.string.go_home); backgroundTintList = ColorStateList.valueOf(accent); setTextColor(Color.BLACK)
                setOnClickListener { onHome() }
            })
            val view = ScrollView(service).apply {
                setBackgroundColor(background or 0xFF000000.toInt())
                contentDescription = "DK blocked pane"
                isFillViewport = true; isClickable = true; addView(column)
            }
            val bounds = pane.bounds
            @android.annotation.SuppressLint("RtlHardcoded") // Bounds are absolute display coordinates; START would move the overlay in RTL.
            val params = WindowManager.LayoutParams(bounds.width, bounds.height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = bounds.left; y = bounds.top
                title = "DK blocked pane"
                if (android.os.Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
            }
            if (runCatching { manager.addView(view, params) }.isSuccess) views[key] = view to pane
        }
    }

    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).toInt()
    private fun remove(key: String) { views.remove(key)?.first?.let { runCatching { manager.removeViewImmediate(it) } } }
    fun clear() { views.keys.toList().forEach(::remove) }
}
