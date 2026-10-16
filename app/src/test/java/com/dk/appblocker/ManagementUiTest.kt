package com.dk.appblocker

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManagementUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val original = BlockPlan(name = "Reading time", triggerType = TriggerType.MANUAL, manualActive = true, packages = listOf("example.app"))
    private fun render(failing: Boolean = false): FailingStorage {
        val context = FailingStorage(compose.activity, compose.activity)
        compose.activity.getSharedPreferences("dk_blocker_prefs", 0).edit().clear().commit()
        Prefs.savePlans(context, listOf(original))
        context.failWrites = failing
        val generation = java.util.UUID.randomUUID().toString()
        compose.runOnUiThread { compose.activity.setContent {
            CompositionLocalProvider(LocalContext provides context) { key(generation) { DKTheme { AppRoot() } } }
        } }
        return context
    }
    private fun rules() { compose.onNodeWithTag("homeList").performScrollToNode(hasTestTag("seeAllRules")); compose.onNodeWithTag("seeAllRules").performClick() }
    private fun menu(name: String) { compose.onNodeWithTag("rulesList").performScrollToNode(hasContentDescription("Actions for $name")); compose.onNodeWithContentDescription("Actions for $name").performClick() }
    @Test fun seeAllOpensRulesAndExistingEditorBackReturnsToRules() {
        render(); rules()
        compose.onNodeWithText("Your automatic blocking routines.").assertExists()
        compose.onNodeWithText(original.name).performClick()
        compose.onNodeWithText("Edit rule").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("rulesList").assertExists()
        compose.onNodeWithText("Create Rule").assertDoesNotExist()
    }
    @Test fun createBackReturnsToTypeSelectionAndAndroidBackReturnsToRules() {
        render(); rules()
        compose.onNodeWithContentDescription("Create").performClick()
        compose.onNodeWithText("Schedule").performClick()
        compose.onNodeWithText("New rule").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Create Rule").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("rulesList").assertExists()
    }
    @Test fun duplicateAndConfirmedDeleteAreDurableAndCancelKeepsTheRule() {
        val context = render(); rules(); menu(original.name)
        compose.onNodeWithText("Duplicate").performClick()
        compose.waitUntil(5000) { Prefs.getPlans(context).size == 2 }
        menu("Reading time (copy)"); compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Keep rule").performClick()
        assertEquals(2, Prefs.getPlans(context).size)
        menu("Reading time (copy)"); compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Delete rule").performClick()
        compose.waitUntil(5000) { Prefs.getPlans(context).size == 1 }
        assertEquals(original.id, Prefs.getPlans(context).single().id)
    }
    @Test fun failedMutationShowsAnErrorAndKeepsTheVisibleRule() {
        val context = render(true); rules(); menu(original.name)
        compose.onNodeWithText("Duplicate").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("storageError").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, Prefs.getPlans(context).size)
        compose.onNodeWithText(original.name).assertExists()
        compose.onNodeWithTag("rulesList").assertExists()
    }
    @Test fun strictModeRejectsUiDuplicationAndKeepsRecoveryVisible() {
        val context = render()
        Prefs.saveStrict(context, StrictSettings(enabled = true, unlockMethod = "TIMER", lockUntil = System.currentTimeMillis() + 60_000,
            lockElapsedEnd = android.os.SystemClock.elapsedRealtime() + 60_000, lockBoot = Prefs.boot(context)))
        rules(); menu(original.name); compose.onNodeWithText("Duplicate").performClick()
        compose.onNodeWithTag("strictList").assertExists()
        compose.onNodeWithTag("strictList").performScrollToNode(hasText("Open Android Settings"))
        compose.onNodeWithText("Open Android Settings").assertIsDisplayed()
        assertEquals(1, Prefs.getPlans(context).size)
    }
    @Test fun strictSliderChangesStayDraftUntilSavedAndActivationCannotPrecedeSave() {
        val context = render()
        Prefs.saveStrict(context, StrictSettings(unlockMethod = "TIMER"))
        compose.runOnUiThread { compose.activity.setContent {
            CompositionLocalProvider(LocalContext provides context) { DKTheme { StrictModeScreen({}, {}) } }
        } }
        compose.onNodeWithTag("strictCooldown").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        val commits = context.commits
        compose.waitForIdle()
        assertEquals(commits, context.commits)
        assertEquals(0, Prefs.getStrict(context).editCooldownMinutes)
        compose.onNodeWithTag("strictList").performScrollToNode(hasTestTag("strictActivate"))
        compose.onNodeWithTag("strictActivate").assertIsNotEnabled()
        compose.onNodeWithTag("strictList").performScrollToNode(hasTestTag("saveStrictSettings"))
        compose.onNodeWithTag("saveStrictSettings").performClick()
        compose.waitUntil(5000) { Prefs.getStrict(context).editCooldownMinutes == 30 }
        compose.onNodeWithTag("strictList").performScrollToNode(hasTestTag("strictActivate"))
        compose.onNodeWithTag("strictActivate").assertIsEnabled()
    }
    @Test fun failedStrictActivationDoesNotShowAnActivatedState() {
        val context = render()
        Prefs.saveStrict(context, StrictSettings(unlockMethod = "TIMER"))
        context.failWrites = true
        compose.runOnUiThread { compose.activity.setContent {
            CompositionLocalProvider(LocalContext provides context) { DKTheme { StrictModeScreen({}, {}) } }
        } }
        compose.onNodeWithTag("strictList").performScrollToNode(hasTestTag("strictActivate"))
        compose.onNodeWithTag("strictActivate").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("strictNotice").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("strictList").performScrollToNode(hasTestTag("strictPersistedStatus"))
        compose.onNodeWithTag("strictPersistedStatus").assertTextEquals("○ Protection inactive")
        assertFalse(Prefs.getStrict(context).enabled)
    }
    @Test fun expiredTimerWithFailedStorageShowsRecoveryPendingRatherThanSavedInactive() {
        val context = render()
        Prefs.saveStrict(context, StrictSettings(enabled = true, unlockMethod = "TIMER", lockUntil = 0L,
            lockElapsedEnd = 0L, lockBoot = Prefs.boot(context)))
        context.failWrites = true
        compose.runOnUiThread { compose.activity.setContent {
            CompositionLocalProvider(LocalContext provides context) { DKTheme { StrictModeScreen({}, {}) } }
        } }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("strictPersistedStatus").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("strictPersistedStatus").assertTextEquals("Recovery active · cleanup not saved")
        compose.onNodeWithText("○ Protection inactive").assertDoesNotExist()
        assertTrue(Prefs.canEditRules(context))
    }
}
