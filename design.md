# Design Document: Total Audio Hub (Simple Music)

## 1. Overview
A dedicated audio hub for Android (Moto G04s) focused on offline reliability, independent state management, and a high-visibility "Deck-based" UI.

## 2. Architecture: The Independent Hub
The app is divided into three distinct modules that share a media engine (ExoPlayer) but maintain 100% independent state.

- **Music Module:** Folder-based MP3 player with persistent "Decks."
- **Radio Module:** Live streaming with favorite stations.
- **Podcast Module:** RSS-based downloader and offline player.

---

## 3. Music Module: UX/UI Design (The "Deck" System)

### A. The Dashboard (Main Music Screen)
The home screen consists of a scrollable list of **Big Buttons** (Decks).
- **One Button = One State:** Each button represents a specific listening context.
- **Visual Metadata:**
    - **Name:** User-defined name.
    - **Folder:** Current linked folder.
    - **Current Song:** Title of the song last played.
    - **Progress:** A visual bar and timestamp (e.g., `12:45 / 45:00`).
- **Action:**
    - **Tap:** Opens the **Player Screen** and resumes playback.
    - **Long Press:** Quick-menu (Rename/Delete).
- **Add Button:** Large "+" button at the bottom.

### B. The Player Screen
- **Focus:** Immersive view with large transport controls and album art.
- **Navigation:** Back arrow returns to Dashboard (audio continues).

---

## 4. Radio Module: UX/UI Design (The "Station" System)

### A. The Dashboard (Main Radio Screen)
Similar to the Music module, but optimized for live streams. No detail screen is required; all interaction happens here.
- **Big Buttons (Stations):**
    - **Station Name:** (e.g., "BBC Radio 1").
    - **Now Playing:** Real-time ICY metadata (Artist - Song).
    - **Status:** "Live" indicator or "Buffering..." spinner.
- **Actions:**
    - **Single Tap:** Toggles Play/Stop for that station.
    - **Active State:** The button of the currently playing station is highlighted (e.g., primary color border/glow).
    - **Long Press:** Edit URL, Rename, or Delete.
- **Add Button:** Large "+" button.

---

## 5. Podcast Module: (Design Pending)

---

## 6. Database Schema (Room)

### Table: `audio_channels` (Decks/Stations)
| Column | Type | Description |
| :--- | :--- | :--- |
| `id` | Int (PK) | Unique ID |
| `name` | String | User-defined name |
| `type` | Enum | FOLDER, RADIO, or PODCAST |
| `folderUri` | String? | URI to local folder |
| `streamUrl` | String? | Radio Stream URL or RSS Feed URL |
| `currentTrackUri` | String? | URI of specific file (for Music) |
| `currentPositionMs`| Long | Position for resumption |
| `currentTrackTitle`| String?| Last known song title (for Dashboard) |

---

## 7. Questions & Suggestions for Radio

1. **Stop Action:** Since there is no detail screen, should tapping the button again stop the radio, or just pause it?
2. **Search Discovery:** Should we integrate a **Radio Browser** search feature so you can find stations by name/country instead of typing URLs?
3. **Global Mini-Player:** Would you like a small "Now Playing" bar at the very bottom of the screen that is visible regardless of which tab (Music/Radio/Podcast) you are in?
