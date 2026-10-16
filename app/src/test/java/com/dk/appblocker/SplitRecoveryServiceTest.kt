package com.dk.appblocker

import android.content.Context
import android.graphics.Rect
import android.os.PowerManager
import android.app.KeyguardManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inspector.WindowInspector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SplitRecoveryServiceTest {
    @Test fun focusExpiryRemovesAnOverlayWithoutWindowChangesButKeepsIndependentBlocks() {
        val controller = Robolectric.buildService(BlockAccessibilityService::class.java).create()
        val service = controller.get()
        try {
            service.getSharedPreferences("dk_blocker_prefs", 0).edit().clear().commit()
            shadowOf(service.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(true)
            shadowOf(service.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setKeyguardLocked(false)
            fun window(id: Int, pkg: String, top: Int, bottom: Int, focused: Boolean) = AccessibilityWindowInfo.obtain().also {
                shadowOf(it).apply {
                    setId(id); setType(AccessibilityWindowInfo.TYPE_APPLICATION); setBoundsInScreen(Rect(0, top, 320, bottom))
                    setFocused(focused); setActive(focused)
                    setRoot(AccessibilityNodeInfo.obtain().apply { packageName = pkg })
                }
            }
            val pkg = "example.blocked"
            shadowOf(service).setWindows(listOf(window(1, "com.android.settings", 0, 220, true), window(2, pkg, 230, 470, false)))
            fun overlays() = WindowInspector.getGlobalWindowViews().count { it.contentDescription == "DK blocked pane" }
            Prefs.startFocusSession(service, setOf(pkg), 1000)
            runBlocking { service.checkVisibleWindows() }; assertEquals(1, overlays())
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1))
            runBlocking { service.checkVisibleWindows() }; assertEquals(0, overlays())
            Prefs.startFocusSession(service, setOf(pkg), 1000)
            Prefs.savePlans(service, listOf(BlockPlan(triggerType = TriggerType.MANUAL, packages = listOf(pkg), manualActive = true)))
            runBlocking { service.checkVisibleWindows() }; assertEquals(1, overlays())
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1))
            runBlocking { service.checkVisibleWindows() }; assertEquals(1, overlays())
            assertNull(Prefs.focusSession(service))
        } finally { controller.destroy() }
    }
    @Test fun actualServiceInspectsOtherPaneWhileSettingsHasFocusAndRemovesOverlayOnRecoveryFullscreen() {
        val controller = Robolectric.buildService(BlockAccessibilityService::class.java).create()
        val service = controller.get()
        try {
            service.getSharedPreferences("dk_blocker_prefs", 0).edit().clear().commit()
            shadowOf(service.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(true)
            shadowOf(service.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setKeyguardLocked(false)
            Prefs.savePlans(service, listOf(BlockPlan(triggerType = TriggerType.MANUAL, packages = listOf("com.example.blocked"), manualActive = true)))
            fun window(id: Int, pkg: String, bounds: Rect, focused: Boolean): AccessibilityWindowInfo =
                AccessibilityWindowInfo.obtain().also { info ->
                    shadowOf(info).apply {
                        setId(id); setType(AccessibilityWindowInfo.TYPE_APPLICATION); setBoundsInScreen(bounds)
                        setFocused(focused); setActive(focused)
                        setRoot(AccessibilityNodeInfo.obtain().apply { packageName = pkg })
                    }
                }
            val settings = window(1, "com.android.settings", Rect(0, 0, 320, 220), true)
            val blocked = window(2, "com.example.blocked", Rect(0, 230, 320, 470), false)
            shadowOf(service).setWindows(listOf(settings, blocked))
            runBlocking { service.checkVisibleWindows() }
            val overlays = WindowInspector.getGlobalWindowViews().filter { it.contentDescription == "DK blocked pane" }
            assertEquals("Settings focus must not suppress the blocked-pane overlay", 1, overlays.size)
            val params = overlays.single().layoutParams as android.view.WindowManager.LayoutParams
            assertTrue(params.y >= 230)
            assertTrue(params.y + params.height <= 470)
            assertTrue(params.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
            shadowOf(service).setWindows(listOf(window(1, "com.android.settings", Rect(0, 0, 320, 470), true)))
            runBlocking { service.checkVisibleWindows() }
            assertTrue(WindowInspector.getGlobalWindowViews().none { it.contentDescription == "DK blocked pane" })
        } finally { controller.destroy() }
    }
}
