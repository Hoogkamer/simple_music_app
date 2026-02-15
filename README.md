# Simple Music Player

A folder-based Android music player designed for users who want to switch between different "listening states" (e.g., Train, Walking, Home). Each state remembers its own selected folder, current track, and playback position.

## Features

- **Up to 5 Listening States**: Create custom states with unique names.
- **Context Awareness**: Seamlessly switch between states and resume exactly where you left off.
- **Recursive Folder Scanning**: Plays all MP3 files within a selected folder and its subdirectories.
- **Media3 (ExoPlayer) Integration**: Robust background playback with notification and Bluetooth controls.
- **Material 3 Design**: Modern, clean UI with dynamic color support (Android 12+).

## Tech Stack

- **Kotlin**: Primary language.
- **Jetpack Compose**: Modern declarative UI.
- **Media3**: For high-performance audio playback.
- **Room**: For persistence of listening states.

## How to Install on Your Phone

### Prerequisites

1.  **Enable Developer Options** on your Android phone:
    - Go to **Settings** > **About phone**.
    - Tap **Build number** 7 times until you see "You are now a developer!".
2.  **Enable USB Debugging**:
    - Go to **Settings** > **System** > **Developer options**.
    - Toggle **USB debugging** to ON.
3.  **Connect your phone** to your computer via USB.

### Method 1: Using Android Studio (Recommended)

1.  Open this project in **Android Studio**.
2.  Select your phone from the device dropdown in the top toolbar.
3.  Click the **Run** button (green play icon) or press `Shift + F10`.

### Method 2: Using the Command Line (ADB)

If you have the Android SDK installed, you can build and install the APK manually:

1.  Open a terminal in the project root.
2.  Build the debug APK:
    ```bash
    ./gradlew assembleDebug
    ```
3.  Install the APK to your connected phone:
    ```bash
    adb install app/build/outputs/apk/debug/app-debug.apk
    ```

## How to Use

1.  **Open the App**: You'll see an option to create a Listening State.
2.  **Create a State**: Tap `Create State` and give it a name like "Train Ride".
3.  **Choose a Folder**: Tap `Select Folder` and navigate to a folder on your phone containing MP3 files. Grant the app permission to access that folder.
4.  **Play Music**: The app will scan for MP3s and start playing. You can shuffle, repeat, and skip tracks.
5.  **Switch Contexts**: Use the dropdown at the top to switch to another state (e.g., "Gym"). When you switch back to "Train Ride", it will resume the same track at the exact same second you left it.

## Permissions

- **Notification Permission**: Used to show playback controls on your lock screen and notification shade.
- **Storage Access (SAF)**: Used to read your music folders without requiring full file system access.
