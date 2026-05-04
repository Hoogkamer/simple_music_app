# Total Audio Hub

A comprehensive Android audio center designed for independent management of Music, Radio, and Podcasts. Built for users who value offline reliability, context-specific state management, and high-visibility controls.

## Features

- **Music Decks**: Folder-based playback with independent position memory for every "Deck" (context).
- **Radio Stations**: Global station search and live streaming with real-time metadata.
- **Podcasts**: RSS subscription, background downloading, and smart queue management.
- **Context Resumption**: Seamlessly switch between a 2-hour DJ mix, a live radio stream, and a podcast episode without losing your place.
- **Media3 (ExoPlayer) Integration**: Robust background playback with system-level notification and Bluetooth controls.
- **Material 3 Design**: Modern UI with dynamic color support.

## Documentation

- **[User Manual](user_manual.md)**: A complete guide on how to use all features of the app.
- **[Technical Architecture](architecture.md)**: Deep dive into the data model, module structure, and media engine.

## Tech Stack

- **Kotlin**: Primary language.
- **Jetpack Compose**: Modern declarative UI.
- **Media3 (ExoPlayer)**: High-performance audio playback.
- **Room**: Persistent storage for states and podcast data.
- **WorkManager**: Reliable background downloading for podcasts.

## Installation

### Prerequisites
1.  **Enable Developer Options** on your Android phone.
2.  **Enable USB Debugging**.
3.  **Connect your phone** to your computer via USB.

### Method 1: Android Studio (Recommended)
1.  Open this project in **Android Studio**.
2.  Select your phone from the device dropdown.
3.  Click the **Run** button or press `Shift + F10`.

### Method 2: Command Line (ADB)
```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Permissions
- **Notifications**: For playback controls in the shade and lock screen.
- **Storage Access (SAF)**: To read your music folders securely.
- **Internet**: To fetch radio streams and podcast RSS feeds.

