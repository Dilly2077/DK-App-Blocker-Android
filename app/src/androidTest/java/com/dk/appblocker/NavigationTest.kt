package com.dk.appblocker

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun swipeAndBottomNavigationReachAllTabs() {
        compose.onNodeWithTag("mainPager").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Your automatic blocking routines.").assertIsDisplayed()
        compose.onNodeWithTag("mainPager").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Screen-time access required").assertIsDisplayed()
        compose.onNodeWithTag("mainPager").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Local tools, no subscription").assertIsDisplayed()
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithText("Stay focused\ntoday 🍃").assertIsDisplayed()
    }
}
