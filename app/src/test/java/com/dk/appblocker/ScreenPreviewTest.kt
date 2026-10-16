package com.dk.appblocker

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Locally rendered production Compose previews, explicitly NOT emulator/device screenshots. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val rule = BlockPlan(name = "Morning reading", triggerType = TriggerType.SCHEDULE, packages = listOf("example.reader"))
    private fun seed() {
        compose.activity.getSharedPreferences("dk_blocker_prefs", 0).edit().clear().commit()
        Prefs.savePlans(compose.activity, listOf(rule, rule.copy(id = "manual", name = "Evening quiet", triggerType = TriggerType.MANUAL, manualActive = true)))
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        lateinit var bitmap: Bitmap
        compose.runOnUiThread {
            val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
        }
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
        val sampled = mutableSetOf<Int>()
        for (x in 0 until bitmap.width step 12) for (y in 0 until bitmap.height step 12) sampled += bitmap.getPixel(x, y)
        assertTrue("Rendered preview must not be a blank image", sampled.size > 8)
        val file = File("build/outputs/ui-preview/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        println("Local Robolectric preview: ${file.absolutePath}")
    }
    @Test fun captureDashboardAndRuleManagement() {
        seed(); Prefs.startFocus(compose.activity, setOf("example.reader", "example.social"), 90)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("focusCountdown").performScrollTo().assertExists()
        screenshot("home-focus")
        compose.onNodeWithTag("homeList").performScrollToNode(hasTestTag("seeAllRules"))
        compose.onNodeWithTag("seeAllRules").performClick()
        compose.onNodeWithTag("rulesList").assertExists()
        screenshot("rules")
    }
    @Test fun captureInsightsAndSettings() {
        seed()
        Prefs.addBlockEvent(compose.activity, BlockEvent(System.currentTimeMillis(), "example.reader", "Schedule"))
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Insights", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Week").performClick()
        compose.onNodeWithTag("insightPeriodHeading").assertTextEquals("Last 7 days")
        screenshot("insights-week")
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("settingsList").assertExists(); screenshot("settings")
    }
    @Test fun captureStrictEditorAndCustomBlockingScreen() {
        seed()
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Strict Mode").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("strictPersistedStatus").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("strictPersistedStatus").assertExists(); screenshot("strict")
        compose.runOnUiThread { compose.activity.setContentView(android.widget.FrameLayout(compose.activity)); compose.activity.setContent { DKTheme { RuleEditorScreen(rule, onBack = {}, onSave = {}, existing = true) } } }
        compose.onNodeWithTag("ruleEditor").assertExists(); screenshot("rule-editor")
        compose.runOnUiThread { compose.activity.setContentView(android.widget.FrameLayout(compose.activity)); compose.activity.setContent { DKTheme { BlockedScreen("Social", "Morning reading · scheduled block", false, true, {}, {}) } } }
        compose.onNodeWithTag("mainPager").assertDoesNotExist()
        compose.onNodeWithText("Go home").assertIsDisplayed(); screenshot("blocking-screen")
    }
    @Test fun periodAndEditorDraftSurviveActivityRecreation() {
        seed(); compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Insights", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Month").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("insightPeriodHeading").assertTextEquals("Last 30 days")
        compose.onNodeWithText("Rules", useUnmergedTree = true).performClick()
        compose.onNodeWithText(rule.name).performClick()
        compose.onNodeWithTag("ruleName").performTextReplacement("Unsaved title")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("ruleName").assertTextContains("Unsaved title")
        assertEquals("Morning reading", Prefs.getPlans(compose.activity).first().name)
    }
    @Test fun strictDraftSurvivesRecreationWithoutAWrite() {
        seed(); Prefs.saveStrict(compose.activity, StrictSettings(unlockMethod = "TIMER"))
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Strict Mode").performClick()
        compose.onNodeWithTag("strictCooldown").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("strictList").performScrollToNode(hasTestTag("strictUnsaved"))
        compose.onNodeWithTag("strictUnsaved").assertExists()
        assertEquals(0, Prefs.getStrict(compose.activity).editCooldownMinutes)
    }
    @Test @Config(qualifiers = "w320dp-h568dp-mdpi")
    fun smallScreenLargeFontsKeepFocusControlsAndRecoveryReachable() {
        seed(); Prefs.startFocus(compose.activity, setOf("example.reader"), 90)
        compose.runOnUiThread { compose.activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.6f)) { DKTheme { AppRoot() } }
        } }
        compose.onNodeWithTag("homeList").performScrollToNode(hasTestTag("endFocus"))
        compose.onNodeWithTag("endFocus").performScrollTo().assertIsDisplayed()
        screenshot("small-large-font-focus")
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Strict Mode").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("strictPersistedStatus").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("strictList").performScrollToNode(hasText("Open Android Settings"))
        compose.onNodeWithText("Open Android Settings").performScrollTo().assertIsDisplayed()
    }
    @Test @Config(qualifiers = "w740dp-h360dp-land-mdpi")
    fun landscapeEditorCanScrollToSaveAndBlockingScreenRetainsHome() {
        seed()
        compose.runOnUiThread { compose.activity.setContent { DKTheme { RuleEditorScreen(rule, onBack = {}, onSave = {}) } } }
        compose.onNodeWithText("Save Rule").performScrollTo().assertIsDisplayed()
        screenshot("landscape-editor")
        compose.runOnUiThread { compose.activity.setContent { DKTheme { BlockedScreen("Reader", "Focus session", false, true, {}, {}) } } }
        compose.onNodeWithText("Go home").performScrollTo().assertIsDisplayed()
    }
}
