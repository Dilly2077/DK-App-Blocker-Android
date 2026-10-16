package com.dk.appblocker

import android.content.ComponentName
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FocusHomeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun realHomeBannerFollowsGrantRevokeRestoreAndKeepsUsageSeparate() {
        val context = compose.activity
        org.robolectric.Shadows.shadowOf(context.getSystemService(android.app.AppOpsManager::class.java)).setMode(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName, android.app.AppOpsManager.MODE_IGNORED)
        fun grant(enabled: Boolean) {
            compose.runOnUiThread {
                Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    if (enabled) ComponentName(context, BlockAccessibilityService::class.java).flattenToShortString() else "")
            }
            compose.waitForIdle()
        }
        grant(false); compose.onNodeWithTag("blockingSetupBanner").assertExists()
        grant(true); compose.onNodeWithTag("blockingSetupBanner").assertDoesNotExist()
        compose.onNodeWithTag("usageSetupBanner").assertExists()
        grant(false); compose.onNodeWithTag("blockingSetupBanner").assertExists()
        grant(true); compose.onNodeWithTag("blockingSetupBanner").assertDoesNotExist()
    }
    @Test fun returningFromSettingsRefreshesImmediatelyAndBackgroundStopsUiTicks() {
        var observedTick = 0L
        compose.runOnUiThread {
            compose.activity.setContent {
                observedTick = rememberVisibleTick()
                BlockingSetupStatus(rememberBlockingPermissions(observedTick))
            }
        }
        compose.waitForIdle()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitForIdle()
        val pausedTick = observedTick
        ShadowSystemClock.advanceBy(Duration.ofSeconds(5))
        compose.waitForIdle()
        assertEquals(pausedTick, observedTick)
        Settings.Secure.putInt(compose.activity.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        Settings.Secure.putString(compose.activity.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ComponentName(compose.activity, BlockAccessibilityService::class.java).flattenToString())
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        compose.onNodeWithTag("blockingSetupBanner").assertDoesNotExist()
        assertNotEquals(pausedTick, observedTick)
    }
    @Test fun visibleCountdownAndProgressAdvanceAndEndClearsSession() {
        val context = compose.activity
        context.getSharedPreferences("dk_blocker_prefs", 0).edit().clear().commit()
        Prefs.startFocus(context, setOf("example.app"), 5)
        var tick by mutableLongStateOf(1L)
        compose.runOnUiThread { context.setContent { DKTheme { QuickFocusCard(tick, true) {} } } }
        compose.onNodeWithTag("focusCountdown").assertTextEquals("00:05:00")
        compose.onNodeWithText("example.app").assertExists()
        compose.onNodeWithText("1 app in this session").assertExists()
        compose.runOnUiThread { ShadowSystemClock.advanceBy(Duration.ofSeconds(1)); tick++ }
        compose.onNodeWithTag("focusCountdown").assertTextEquals("00:04:59")
        compose.runOnUiThread { ShadowSystemClock.advanceBy(Duration.ofSeconds(149)); tick++ }
        compose.onNodeWithText("50% complete").assertExists()
        compose.onNodeWithTag("endFocus").performClick()
        compose.waitUntil(5000) { Prefs.focusSession(context) == null }
        compose.onNodeWithTag("focusCountdown").assertDoesNotExist()
        compose.onNodeWithTag("focusHours").assertExists()
    }
    @Test fun expiryUpdatesUiWithoutEndingIndependentRule() {
        val context = compose.activity
        context.getSharedPreferences("dk_blocker_prefs", 0).edit().clear().commit()
        Prefs.startFocusSession(context, setOf("example.app"), 1000)
        Prefs.savePlans(context, listOf(BlockPlan(triggerType = TriggerType.MANUAL, packages = listOf("example.app"), manualActive = true)))
        var tick by mutableLongStateOf(1L)
        compose.runOnUiThread { context.setContent { DKTheme { QuickFocusCard(tick, true) {} } } }
        compose.onNodeWithTag("focusCountdown").assertExists()
        compose.runOnUiThread { ShadowSystemClock.advanceBy(Duration.ofSeconds(1)); tick++ }
        compose.onNodeWithTag("focusCountdown").assertDoesNotExist()
        assertTrue(RuleEngine.shouldBlock(context, "example.app").blocked)
    }
    @Test fun customLongSessionStartsFromInputsAndRestoresAfterActivityRecreation() {
        val context = compose.activity
        context.getSharedPreferences("dk_blocker_prefs", 0).edit().clear().commit()
        Prefs.saveFocusSelection(context, setOf("example.app"))
        compose.runOnUiThread { context.setContent { DKTheme { QuickFocusCard(1, true) {} } } }
        compose.onNodeWithTag("focusMinutes").performTextReplacement("0")
        compose.onNodeWithTag("startFocus").assertIsNotEnabled()
        compose.onNodeWithTag("focusHours").performTextReplacement("25")
        compose.onNodeWithTag("startFocus").assertIsEnabled().performClick()
        compose.waitUntil(5000) { Prefs.focusSession(context) != null }
        compose.onNodeWithTag("focusCountdown").assertTextEquals("1d 01:00:00")
        compose.onNodeWithTag("focusHours").assertDoesNotExist()
        compose.onNodeWithTag("startFocus").assertDoesNotExist()
        val id = Prefs.focusSession(context)!!.id
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("focusCountdown").performScrollTo().assertExists()
        assertEquals(id, Prefs.focusSession(compose.activity)!!.id)
    }
    @Test fun endFocusRoutesToStrictRecoveryWithoutClearingAnActiveSession() {
        val context = compose.activity
        context.getSharedPreferences("dk_blocker_prefs", 0).edit().clear().commit()
        Prefs.startFocus(context, setOf("example.app"), 5)
        Prefs.saveStrict(context, StrictSettings(enabled = true, unlockMethod = "TIMER", lockUntil = System.currentTimeMillis() + 60_000,
            lockElapsedEnd = SystemClock.elapsedRealtime() + 60_000, lockBoot = Prefs.boot(context)))
        var openedStrict = false
        compose.runOnUiThread { context.setContent { DKTheme { QuickFocusCard(1, true) { openedStrict = true } } } }
        compose.onNodeWithTag("endFocus").performClick()
        compose.runOnIdle { assertTrue(openedStrict); assertNotNull(Prefs.focusSession(context)) }
    }
}
