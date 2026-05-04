# Total Audio Hub - Technical Architecture

## 1. Overview

Total Audio Hub is an Android application designed for independent management of three distinct audio categories: Music, Radio, and Podcasts. It focuses on offline reliability, context-specific state management, and a high-visibility UI.

## 2. Core Architecture

The application follows a modular design where each audio type maintains its own independent state while sharing a common media engine.

### A. Media Engine (Media3 / ExoPlayer)

- **Integration:** Built on Google's Media3 library.
- **Background Playback:** Uses a `MediaSessionService` (`MusicService`) to ensure playback continues when the app is backgrounded.
- **Notification & Bluetooth:** Standard system-level controls for play/pause, skipping, and metadata display.
- **State Persistence:** Playback positions and last-active items are persisted to a Room database.

### B. Module Independence

The app is divided into three modules with 100% independent state:

1.  **Music Module:** Folder-based playback with "Decks."
2.  **Radio Module:** URL-based live streaming with metadata parsing.
3.  **Podcast Module:** RSS-based subscription and download management.

---

## 3. Data Model (Room Database)

The application uses a Room database to manage all persistence.

### `audio_channels` Table

This table represents the primary "listening containers" (Decks, Stations, Podcasts).

| Column              | Type     | Description                            |
| :------------------ | :------- | :------------------------------------- |
| `id`                | Int (PK) | Unique identifier                      |
| `name`              | String   | User-defined name                      |
| `type`              | Enum     | `FOLDER`, `RADIO`, or `PODCAST`        |
| `folderUri`         | String?  | URI to local music folder (Music only) |
| `folderDisplayName` | String?  | Human-readable folder name             |
| `streamUrl`         | String?  | Radio Stream URL or RSS Feed URL       |
| `currentTrackUri`   | String?  | URI of the last played file/item       |
| `currentTrackTitle` | String?  | Title of the last played item          |
| `currentTrackArtist`| String?  | Artist of the last played item         |
| `currentTrackAlbum` | String?  | Album of the last played item          |
| `currentPositionMs` | Long     | Playback position for resumption       |
| `currentTrackIndex` | Int      | Index in the track list                |
| `currentTrackDurationMs` | Long | Duration of the current track          |
| `shuffleEnabled`    | Boolean  | Per-deck shuffle state (Music only)    |
| `repeatEnabled`     | Boolean  | Per-deck repeat state (Music only)     |
| `lastPlayedTime`    | Long     | Timestamp used for sorting dashboards  |

### `podcast_episodes` Table

Manages individual episodes for the Podcast module.

| Column               | Type     | Description                                   |
| :------------------- | :------- | :-------------------------------------------- |
| `id`                 | Int (PK) | Unique identifier                             |
| `channelId`          | Int      | FK to `audio_channels.id`                     |
| `title`              | String   | Episode title                                 |
| `description`        | String?  | Episode description / show notes (HTML)       |
| `pubDate`            | Long?    | Publication date timestamp                    |
| `streamUrl`          | String   | Remote audio URL                              |
| `localPath`          | String?  | Path to downloaded file (null if streaming)   |
| `downloadStatus`     | Int      | `IDLE`, `QUEUED`, `DOWNLOADING`, `DOWNLOADED` |
| `downloadProgress`   | Int      | Download progress percentage                  |
| `durationMs`         | Long     | Total episode duration                        |
| `playbackPositionMs` | Long     | Last saved position                           |
| `isFinished`         | Boolean  | Whether the episode has been played           |
| `guid`               | String   | Unique RSS identifier (prevents duplicates)   |
| `podcastTitle`       | String?  | Parent podcast title (for Recent list)        |

---

## 4. Module Specifications

### Music (Decks)

- **Recursive Scanning:** Scans selected folders and subdirectories for MP3 files.
- **Context Resumption:** Each "Deck" remembers its own shuffle/repeat state, current track, and position.

### Radio (Stations)

- **Global Search:** Integrates with the `radio-browser.info` API.
- **Metadata:** Parses ICY metadata to display real-time "Now Playing" information.
- **Streaming:** Handles standard audio stream protocols via ExoPlayer.

### Podcasts

- **RSS Parsing:** Custom parser for standard podcast feeds.
- **Download Management:** Uses `WorkManager` for reliable background downloading.
- **Smart Cleanup:**
  - Automatically deletes local files once an episode is finished (or < 30s remaining).
  - "Mark All Played" skips downloaded/queued episodes to prevent accidental deletion of content intended for listening.

---

## 5. UI/UX Strategy

- **Material 3:** Modern design with dynamic color support.
- **Big Button Design:** High-visibility dashboard cards for easy interaction while moving (Walking/Gym).
- **Global Mini-Player:** A persistent bar at the bottom of the screen showing current playback status regardless of the active module.

## 6. State Management

As this is "three applications in one," the app manages the state of each module independently. Navigation is handled via a bottom bar with three primary destinations. A persistent Mini Player at the bottom of the screen provides global control and status for the currently active audio source.

**App Startup:** On cold launch, the app restores the last active module and channel from persistence, preparing playback at the last known position without auto-playing. All podcast feeds are refreshed in the background.

### Music Module (Decks)

The Music module tracks state per "Deck," allowing users to switch contexts without losing progress.

*   **Persisted State:** Active Deck, last played track within that Deck, playback position, and repeat mode.
*   **Landing Page:** Displays all Decks as cards. Each card shows the Deck's current state (last track, position, progress bar) and a quick Play/Pause button.
    *   **Interaction:** Long-press to rename or delete; short-press to open the Deck Details player.
*   **Deck Details Page:** Provides full playback controls (play/pause, next, previous, seek), folder selection, and a Repeat toggle.

### Radio Module (Stations)

Focuses on immediate access to live streams with minimal navigation overhead.

*   **Persisted State:** The last active Station.
*   **Landing Page:** Displays Stations as cards with integrated Play/Stop toggle buttons.
    *   **Now Playing:** Real-time ICY metadata (Artist/Title) is displayed directly on the active card and in the Mini Player.
    *   **Auto-Naming:** Station names are auto-discovered from ICY stream metadata when the initial name is unknown.
    *   **Management:** Long-press a card to Rename, Edit URL, or Delete. "Add New" button for manual entry and a "Bulk Import" feature for adding multiple URLs via clipboard or text file (with automatic deduplication).
    *   **Global Search:** Integrates with the `radio-browser.info` API to search and add stations by name or country.
*   **Details Page:** Not required; all interactions occur on the Landing Page or Mini Player.

### Podcast Module

Manages complex states for multiple subscriptions and individual episode progress.

*   **Persisted State:** Active Podcast, active episode, and per-episode playback position.
*   **Auto-Naming:** Podcast names are automatically parsed from the RSS feed `<title>` element on subscription.
*   **Background Refresh:** All podcast feeds are refreshed on app launch to fetch new episodes.
*   **Shows Tab:** Displays subscribed Podcasts.
    *   **Interaction:** Short-press leads to the Episode List.
    *   **Bulk Import:** Allows adding multiple RSS feed URLs at once via clipboard or text file.
    *   **Episode List:** Shows episodes ordered by publication date (newest first). Includes a search filter for titles and descriptions.
    *   **Bulk Actions:** "Hide Played" toggles visibility of finished episodes. "Mark All Played" marks all episodes as finished, **excluding those that are downloaded, downloading, or queued** (to prevent accidental deletion of offline content).
*   **Recent Tab:** A chronological feed of episodes from all subscriptions (last 14 days). Includes a "Download All" button for unplayed episodes within that window.
*   **Episode List Item:** Shows a circular progress ring (indicating remaining time), show name, title, and metadata (download status, publication date).
    *   **Interaction:** Swipe left/right to mark as played (with a 5-second undo window); tap Play icon for immediate playback; tap title for details.
*   **Episode Details Page:** Features a playback slider (with skip back 15s / forward 30s), show notes (HTML rendered), and context-aware actions (Download/Stream, Mark Played/Unplayed toggle). Also includes a **Playback Speed** toggle (0.5x to 2.0x).
    *   **Cleanup Logic:** The explicit "Delete" button is removed in favor of automatic cleanup. Marking an episode as **Unplayed** now automatically deletes the associated offline audio file to ensure a clean state.
*   **Back Navigation:** Context-aware — navigating back from Episode Detail returns to the Recent feed or Show Detail, depending on the entry point.

### Global State & UI Components

A shared state layer ensures seamless switching between modules.

*   **Global Persistence (`AppConfig`):** Tracks the last active module (`lastCategory`), active item IDs per module (Deck/Station/Podcast/Episode), and user preferences (e.g., `hidePlayedEpisodes`). Playback positions are persisted per-channel and per-episode in their respective tables.
*   **Mini Player:** A persistent bar at the bottom of the UI showing current metadata, progress, and a play/pause button.
    *   **Navigation:** Tapping the Mini Player navigates to the relevant details page (Deck Details or Episode Details). For Radio, it navigates to the Radio tab.
*   **Widget:** Provides a glanceable UI on the Android home screen with playback controls (Play/Pause, Rewind, Fast Forward) and current track metadata.
    *   **Progress Tracking:** Includes a progress bar and text-based duration/position info (e.g., "1:23 / 4:56") that refreshes every second during playback. For live radio, these elements are hidden or shown as "Live".
