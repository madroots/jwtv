# JW TV

An Android TV application for browsing and watching videos from the JW.org media library. Features a native TV interface with support for video downloads, favorites, watch progress tracking, and quality selection.

## Features

- **Browse JW Library** — Explore video categories (Studio, Children, Family, Bible, Movies, Series, etc.)
- **Video Playback** — Stream videos with resolution selection (up to 2160p)
- **Download & Offline** — Save videos to your device for offline playback
- **Watch Progress** — Automatically resume videos from where you left off, with visual progress indicators on thumbnails
- **Favorites** — Save videos to a personal favorites list
- **Language Support** — Select from 100+ languages available on JW.org
- **Auto-Update** — Built-in update checker downloads and installs new versions directly on your TV

## Screenshots

*(Add screenshots here)*

## Building

```bash
# Clone the repository
git clone https://github.com/madroots/jwtv.git
cd jwtv

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

The built APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Requirements

- **Android TV** device running Android 7.0 (API 24) or later
- Network connection for streaming and updates
- Approximately 500 MB free storage for downloaded content

## Technology Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose for TV (Material3)
- **Media Player:** ExoPlayer / Media3
- **Networking:** OkHttp
- **Persistence:** Room Database (favorites, watch progress, downloads)
- **Image Loading:** Coil
- **Build System:** Gradle 8.8

## License

This project is for personal and educational use. JW Library content is copyrighted by Jehovah's Witnesses and is used in accordance with their terms of service.
