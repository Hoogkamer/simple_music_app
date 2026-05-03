# Podcast Module: UX/UI Design

## Overview
The Podcast Module provides a reliable way to subscribe to RSS feeds, download episodes for offline listening, and easily resume partially played episodes. It follows the consistent "Big Buttons" (Deck) design language of the Music and Radio modules.

## 1. Landing Page
The landing page features a dual-view approach (e.g., using a Top Tab Row or toggle) to serve two distinct user needs:

### Option A: List of Podcasts ("Shows")
- **Big Buttons (Podcasts):** Displays subscribed podcasts as large, easily tappable cards.
    - **Visual Metadata:** Podcast Name, cover art (if available), and unplayed episode count.
- **Action:**
    - **Tap:** Opens the **Podcast Detail Page**.
    - **Long Press:** Quick-menu (Unsubscribe, Edit URL, Rename).
- **Add Button:** Large "+" button at the bottom to subscribe to a new podcast via RSS URL.

### Option B: List of Recent Episodes ("Recent")
A unified feed that *only* shows manually downloaded episodes across all subscribed podcasts.
- **Bulk Action:** A global "Download All New" button to quickly fetch all newly published episodes across all subscriptions.
- **Episode Cards:**
    - **Subject/Title:** Episode title.
    - **Podcast Name:** Source of the episode.
    - **Progress:** Visual indicator of how much has been played (e.g., progress bar or time remaining).
- **Auto-Removal:** Once an episode has less than 30 seconds remaining, or when the user manually marks it as played, it is automatically removed from this Recent list to keep the feed clean.
- **Action:**
    - **Tap:** Immediately resumes playback of the episode.
    - **Mark as Played:** User can manually dismiss an episode from the queue.

## 2. Podcast Detail Page
A dedicated screen for managing a specific podcast subscription.
- **Header:** Podcast Name, description, and "Refresh" button to check for new episodes.
- **Episode List:** A chronological list of all available episodes.
- **Episode Status Indicators:**
    - **Played Status:** Clear visual distinction for "Unplayed", "In Progress", and "Played" episodes.
    - **Download Status:** Icon indicating if the episode is currently downloaded and available offline.
- **Actions per Episode:**
    - **Tap:** Opens the **Episode Detail Page** to view shownotes and full details.
    - **Play/Pause:** Quick action to stream or play the downloaded file directly from the list.
    - **Download/Delete:** Button to manually download the episode for offline listening, or remove the downloaded file. Downloads are entirely manual.
    - **Storage Management:** If an episode is completely played (less than 30 seconds remaining) or marked as played, its downloaded audio file is automatically deleted to save storage space.

## 3. Episode Detail Page
A dedicated screen focused on a single episode, opened when tapping an episode from any list.
- **Content:** Displays the full episode shownotes (description), including clickable links.
- **Metadata:** Episode title, podcast name, publication date, and duration.
- **Actions:** Play/Pause, Download/Delete, and Mark as Played.

## 4. Podcast Player Screen
When an episode is actively playing, the immersive player screen provides podcast-specific controls.
- **Core Controls:** Play/Pause, Progress Slider (Seek bar).
- **Skip Controls:** Dedicated "Skip Back 15s" and "Skip Forward 30s" buttons for easy navigation, replacing or augmenting the standard next/previous track buttons.
