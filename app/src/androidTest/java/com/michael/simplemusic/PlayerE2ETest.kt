package com.michael.simplemusic

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testTabNavigation() {
        // Verify we start on Music tab (default) or at least can click it
        composeTestRule.onNodeWithText("Music").assertExists()
        composeTestRule.onNodeWithText("Radio").assertExists()
        composeTestRule.onNodeWithText("Podcasts").assertExists()

        // Switch to Radio
        composeTestRule.onNodeWithText("Radio").performClick()
        composeTestRule.onNodeWithText("Radio").assertIsSelected()
        
        // Switch to Podcasts
        composeTestRule.onNodeWithText("Podcasts").performClick()
        composeTestRule.onNodeWithText("Podcasts").assertIsSelected()

        // Switch back to Music
        composeTestRule.onNodeWithText("Music").performClick()
        composeTestRule.onNodeWithText("Music").assertIsSelected()
    }
    
    @Test
    fun testClockLaunchButtonExists() {
        // Verify the clock button is present in the top bar
        composeTestRule.onNodeWithContentDescription("Open Clock").assertExists()
    }
}
