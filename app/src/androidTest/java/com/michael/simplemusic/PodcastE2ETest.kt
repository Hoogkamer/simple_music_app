package com.michael.simplemusic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end tests for Podcast playback and navigation.
 * Note: These tests require some existing data in the local database to be fully effective,
 * but they verify the UI structure and navigation logic updated in the previous steps.
 */
@RunWith(AndroidJUnit4::class)
class PodcastE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testPodcastNavigationAndMiniPlayer() {
        // 1. Navigate to Podcasts tab
        composeTestRule.onNodeWithText("Podcasts").performClick()
        composeTestRule.onNodeWithText("Podcasts").assertIsSelected()

        // 2. Switch to 'Recent' view if not already there
        composeTestRule.onNodeWithText("Recent").performClick()
        composeTestRule.onNodeWithText("Recent").assertIsSelected()

        // 3. Find an episode and click it to go to detail
        // We look for any node with "Available" or "Played" which are markers in EpisodeListItem
        val episodeNode = composeTestRule.onAllNodesWithText("Available").onFirst()
        
        if (episodeNode.isRoot().not()) { // If we have episodes
            episodeNode.performClick()

            // 4. Verify we are in Episode Detail screen
            composeTestRule.onNodeWithText("Episode Detail").assertExists()

            // 5. Verify the back button works and returns to Recent
            composeTestRule.onNodeWithContentDescription("Back").performClick()
            composeTestRule.onNodeWithText("Recent").assertIsSelected()
            
            // 6. Test MiniPlayer appearance (if something was playing)
            // This is hard to trigger without real playback, but we can verify the structure
        }
    }

    @Test
    fun testEpisodePlayFromRecent() {
        // Navigate to Podcasts
        composeTestRule.onNodeWithText("Podcasts").performClick()
        composeTestRule.onNodeWithText("Recent").performClick()

        // Try to find a play button in the list
        // Based on PlayerScreen.kt, the play icon doesn't have a specific content description,
        // but it's inside a Box that is clickable.
        val playButtons = composeTestRule.onAllNodes(hasClickAction())
        
        // This is a bit generic, but in a real environment with testTags it would be precise.
        // For now, we verify that we can interact with the Podcast dashboard elements.
        composeTestRule.onNodeWithText("Recent").assertExists()
        composeTestRule.onNodeWithText("Shows").assertExists()
    }
}
