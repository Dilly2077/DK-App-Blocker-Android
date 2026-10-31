package com.dk.appblocker

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Rect
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityRecord
import android.view.accessibility.AccessibilityWindowInfo
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.inspector.WindowInspector
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAccessibilityWindowInfo
import org.robolectric.shadows.ShadowAccessibilityService
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@Implements(AccessibilityWindowInfo::class)
class NoContentWindow : ShadowAccessibilityWindowInfo() {
    @Implementation override fun getRoot(): AccessibilityNodeInfo? = throw AssertionError("Window content retrieval is forbidden")
    @Implementation fun getTitle(): CharSequence? = throw AssertionError("Window title retrieval is forbidden")
    @Implementation override fun getAnchor(): AccessibilityNodeInfo? = throw AssertionError("Anchor content retrieval is forbidden")
}
@Implements(AccessibilityRecord::class)
class NoContentRecord {
    @Implementation fun getSource(): AccessibilityNodeInfo? = throw AssertionError("Event source retrieval is forbidden")
    @Implementation fun getText(): MutableList<CharSequence> = throw AssertionError("Event text retrieval is forbidden")
    @Implementation fun getContentDescription(): CharSequence? = throw AssertionError("Event description retrieval is forbidden")
    @Implementation fun getClassName(): CharSequence? = throw AssertionError("Unrelated class retrieval is forbidden")
}
@Implements(android.accessibilityservice.AccessibilityService::class)
class SubscriptionServiceShadow : ShadowAccessibilityService() {
    private var applied: AccessibilityServiceInfo? = null
    @Implementation fun getServiceInfo(): AccessibilityServiceInfo? = applied
    @Implementation fun setServiceInfo(info: AccessibilityServiceInfo) { applied = info }
}

/** Real service/pane code with root/title/text/source access made fatal by the test Android runtime. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [NoContentWindow::class, NoContentRecord::class, SubscriptionServiceShadow::class])
class PrivacyServiceTest {
    private lateinit var controller: org.robolectric.android.controller.ServiceController<BlockAccessibilityService>
    private lateinit var service: BlockAccessibilityService
    private val social = "example.distraction"
    // Test samples only; production never consults this list.
    private val finance = listOf("uk.co.hsbc.hsbcukmobilebanking", "com.revolut.revolut", "com.starlingbank.android", "future.unknown.finance")
    @Before fun prepare() {
        controller = Robolectric.buildService(BlockAccessibilityService::class.java).create(); service = controller.get()
        service.getSharedPreferences("dk_blocker_prefs", 0).edit().clear().commit()
        shadowOf(service.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(true)
        shadowOf(service.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setKeyguardLocked(false)
    }
    @After fun close() { controller.destroy() }
    private fun plan(pkg: String = social) = BlockPlan(triggerType = TriggerType.MANUAL, packages = listOf(pkg), manualActive = true)
    private fun select(pkg: String = social) { Prefs.savePlans(service, listOf(plan(pkg))); refresh() }
    private fun refresh() = runBlocking { service.refreshMonitoring() }
    private fun check() = runBlocking { service.checkVisibleWindows() }
    private fun event(pkg: String, id: Int) {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event.packageName = pkg
        ReflectionHelpers.setField(event, "mSourceWindowId", id)
        ReflectionHelpers.getField<MutableList<CharSequence>>(event, "mText").add("SYNTHETIC-PRIVATE-ACCOUNT")
        service.onAccessibilityEvent(event)
    }
    private fun window(id: Int, top: Int = 0, bottom: Int = 470, focused: Boolean = true, layer: Int = 1) =
        AccessibilityWindowInfo.obtain().also { info -> shadowOf(info).apply {
            setId(id); setType(AccessibilityWindowInfo.TYPE_APPLICATION); setBoundsInScreen(Rect(0, top, 320, bottom))
            setFocused(focused); setActive(focused); setLayer(layer)
        } }
    private fun windows(vararg windows: AccessibilityWindowInfo) { shadowOf(service).setWindows(windows.toList()) }
    private fun overlays() = WindowInspector.getGlobalWindowViews().filter { it.contentDescription == "DK blocked pane" }

    @Test fun unrelatedFinanceAppsCauseNoWindowOrContentReadsAndNoInterception() {
        select()
        for ((index, pkg) in finance.withIndex()) {
            windows(window(index + 10)); event(pkg, index + 10); check()
            assertTrue(overlays().isEmpty()); assertFalse(RuleEngine.shouldBlock(service, pkg).blocked)
        }
        assertEquals(0, service.windowMetadataReads); assertEquals(0, service.targetEvaluations)
        assertTrue(Prefs.getBlockEvents(service).isEmpty()); assertNull(shadowOf(service).nextStartedActivity)
    }
    @Test fun idleSubscriptionsArePositiveAndInteractiveWindowsAreNotRequested() {
        select()
        assertEquals(setOf(service.packageName, social), service.serviceInfo.packageNames.toSet())
        assertEquals(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, service.serviceInfo.eventTypes)
        assertEquals(0, service.serviceInfo.flags)
        assertEquals(0, service.windowMetadataReads)
    }
    @Test fun emptySelectionNeverBecomesTheAndroidAllPackagesWildcard() {
        refresh(); assertEquals(setOf(service.packageName), service.serviceInfo.packageNames.toSet())
        finance.forEach { event(it, 10) }; check()
        assertEquals(0, service.windowMetadataReads); assertTrue(overlays().isEmpty())
    }
    @Test fun selectedSocialPaneBlocksBesideFocusedFinanceWithoutReadingEitherRoot() {
        select(); windows(window(1, 230, 470, true, 2), window(2, 0, 220, false))
        event(social, 2); event(finance.first(), 1); check()
        val overlay = overlays().single()
        val params = overlay.layoutParams as android.view.WindowManager.LayoutParams
        assertTrue(params.y >= 0); assertTrue(params.y + params.height <= 220)
        assertEquals(setOf(social), Prefs.getBlockEvents(service).map { it.packageName }.toSet())
        assertNull(shadowOf(service).nextStartedActivity) // No automatic full-screen activity.
    }
    @Test fun unknownOverlappingFinanceWindowsTakePriorityOverBlockingSurfaces() {
        select(); windows(window(1, layer = 2), window(2, focused = false))
        event(social, 2); event(finance[1], 1); check()
        assertTrue(overlays().isEmpty()); assertTrue(Prefs.getBlockEvents(service).isEmpty())
    }
    @Test fun aFinancialPackageIsBlockableOnlyWhenExplicitlySelectedAndNoContentIsRead() {
        select(finance[2]); windows(window(2)); event(finance[2], 2); check()
        assertEquals(1, overlays().size)
        assertEquals(finance[2], Prefs.getBlockEvents(service).single().packageName)
        assertFalse(RuleEngine.shouldBlock(service, finance[3]).blocked)
    }
    @Test fun rapidWindowIdReuseCannotLeaveASocialOverlayOnAnUnrelatedApp() {
        select()
        repeat(4) {
            windows(window(2)); event(social, 2); check(); assertEquals(1, overlays().size)
            event(finance[it], 2); assertTrue("Clear synchronously on switch", overlays().isEmpty())
            windows(window(2)); check(); assertTrue(overlays().isEmpty())
            assertNotNull(service.serviceInfo.packageNames)
        }
        assertTrue(Prefs.getBlockEvents(service).all { it.packageName == social })
    }
    @Test fun policyChangesRemoveOldTargetsAndUpdateSubscriptionsWithoutStaleCoverage() {
        select(); windows(window(2)); event(social, 2); check(); assertEquals(1, overlays().size)
        select("new.distraction"); check()
        assertTrue(overlays().isEmpty()); assertFalse(social in service.serviceInfo.packageNames)
        assertTrue("new.distraction" in service.serviceInfo.packageNames)
        event("new.distraction", 2); check(); assertEquals(1, overlays().size)
        Prefs.savePlans(service, Prefs.getPlans(service).map { it.copy(enabled = false) }); check()
        assertTrue(overlays().isEmpty()); assertEquals(setOf(service.packageName), service.serviceInfo.packageNames.toSet())
    }
    @Test fun expiryRestoresOnlyTheFocusTargetWhileAnotherRuleRemainsEffective() {
        Prefs.startFocusSession(service, setOf(social), 1000); refresh()
        windows(window(1, 230, 470), window(2, 0, 220, false))
        event(social, 2); event(finance.first(), 1); check(); assertEquals(1, overlays().size)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1)); check(); assertTrue(overlays().isEmpty())
        assertNotNull(service.serviceInfo.packageNames)
        select(); event(social, 2); check(); assertEquals(1, overlays().size)
    }
    @Test fun globalBoundarySubscriptionEndsWhenTheTrackedTargetLeaves() {
        select(); windows(window(2)); event(social, 2); check()
        assertNull(service.serviceInfo.packageNames)
        assertEquals(AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS, service.serviceInfo.flags)
        windows(window(1)); event(finance.first(), 1)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2)); check()
        assertNotNull(service.serviceInfo.packageNames); assertEquals(0, service.serviceInfo.flags)
        val reads = service.windowMetadataReads; event(finance[1], 1); check(); assertEquals(reads, service.windowMetadataReads)
    }
    @Test fun strictModeAddsOnlyNavigationHintsAndDoesNotRequireDeviceAdministrator() {
        select()
        Prefs.saveStrict(service, StrictSettings(enabled = true, pinHash = "configured", unlockMethod = "PASSWORD")); refresh()
        assertTrue("com.android.systemui" in service.serviceInfo.packageNames)
        finance.forEach { event(it, 5) }; check()
        assertFalse(isDeviceAdminActive(service)); assertTrue(overlays().isEmpty()); assertEquals(0, service.windowMetadataReads)
        assertTrue(Prefs.disableStrictDurably(service)); refresh()
        assertFalse("com.android.systemui" in service.serviceInfo.packageNames)
    }
    @Test fun newServiceAndRebootDoNotTrustPersistedOrReusedWindowIds() {
        select(); windows(window(2)); event(social, 2); check(); assertEquals(1, overlays().size)
        controller.destroy()
        controller = Robolectric.buildService(BlockAccessibilityService::class.java).create(); service = controller.get()
        windows(window(2)); refresh(); check()
        assertTrue(overlays().isEmpty()); assertEquals(0, service.windowMetadataReads)
        event(social, 2); check(); assertEquals(1, overlays().size)
        Prefs.startFocusSession(service, setOf("temporary.app"), 60_000)
        android.provider.Settings.Global.putInt(service.contentResolver, android.provider.Settings.Global.BOOT_COUNT, Prefs.boot(service) + 1)
        refresh(); assertFalse("temporary.app" in RuleEngine.selectedPackages(service))
    }
    @Test fun missingOrAmbiguousWindowIdentityDoesNotJustifyCoveringAnotherApplication() {
        select(); windows(window(2)); event(social, -1); check(); assertTrue(overlays().isEmpty())
        event(social, 2); check(); assertEquals(1, overlays().size)
        event(finance[3], -1); check(); assertTrue(overlays().isEmpty())
    }
    @Test fun ownOverlayWindowEventsDoNotCauseClearAndRecreateLoops() {
        select(); windows(window(2)); event(social, 2); check()
        val existing = overlays().single()
        val own = window(99, focused = false, layer = 10)
        shadowOf(own).setType(AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY)
        windows(own, window(2))
        val change = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOWS_CHANGED)
        ReflectionHelpers.setField(change, "mSourceWindowId", 99)
        service.onAccessibilityEvent(change)
        assertSame(existing, overlays().single())
        check(); assertSame(existing, overlays().single())
    }
    @Test fun directOverlayRequestsCannotBypassTheExplicitSelectionPolicy() {
        select()
        val overlay = PaneBlockOverlay(service)
        val bounds = PaneProtection.Bounds(0, 0, 320, 220)
        overlay.update(listOf(PaneBlockOverlay.Pane("bank", finance[0], bounds, "not selected")), setOf(finance[0]), {}, {})
        assertTrue(overlays().isEmpty())
    }
    @Test fun connectedServiceAutomaticallyRefreshesSubscriptionsWhenDurableSelectionChanges() {
        select()
        ReflectionHelpers.callInstanceMethod<Unit>(service, "onServiceConnected")
        Prefs.savePlans(service, listOf(plan("changed.distraction")))
        var updated = false
        for (attempt in 0..400) {
            shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
            updated = service.serviceInfo.packageNames?.contains("changed.distraction") == true
            if (updated) break
            Thread.sleep(5)
        }
        assertTrue("The running service must observe preference changes", updated)
        assertFalse(social in service.serviceInfo.packageNames)
        assertEquals(0, service.windowMetadataReads)
    }
    @Test fun permissionRevocationDestroyAndRestorationClearSurfacesAndStartWithNoTrustedIds() {
        select(); windows(window(2)); event(social, 2); check(); assertEquals(1, overlays().size)
        android.provider.Settings.Secure.putInt(service.contentResolver, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        controller.destroy(); assertTrue(overlays().isEmpty()); assertFalse(isAccessibilityEnabled(service))
        controller = Robolectric.buildService(BlockAccessibilityService::class.java).create(); service = controller.get()
        android.provider.Settings.Secure.putInt(service.contentResolver, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        android.provider.Settings.Secure.putString(service.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            android.content.ComponentName(service, BlockAccessibilityService::class.java).flattenToShortString())
        refresh(); check(); assertTrue(isAccessibilityEnabled(service)); assertTrue(overlays().isEmpty())
        windows(window(2)); event(social, 2); check(); assertEquals(1, overlays().size)
    }
}
