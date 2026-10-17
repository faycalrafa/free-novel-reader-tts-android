# BookReader 📖✨

> A personal offline novel reader and Text-to-Speech (TTS) audiobook application for Android.
> No Ads • No Trackers • No Subscriptions • Fully Offline • Native Android & Text-to-Speech

[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material3-deepskyblue.svg)](https://developer.android.com/jetpack/compose)

---

## 🌟 What is BookReader?

**BookReader** is a personal hobby project created and maintained by Faycal Rafa. It is a high-performance Android reader and offline Text-to-Speech (TTS) audiobook player built for readers of web novels, light novels, eBooks, and multi-file text collections.

Unlike standard e-readers that treat individual chapters as disconnected documents, BookReader treats your **local folders as dynamic audio playlists**:
1. Simply select any root novel directory on your device.
2. Every subfolder is automatically detected and indexed as its own novel/playlist.
3. Chapters (`.txt`, `.html`, `.xhtml`, `.md`, `.epub`) are naturally sorted in order.
4. Each playlist independently remembers your current chapter, paragraph index, and scroll position.

Read visually with dynamic paragraph highlighting and auto-scrolling, or listen hands-free in the background with lockscreen controls and earbud media keys!

---

## 🚀 Key Features

### 🎧 Seamless Background Text-to-Speech & Media Controls
- **Full MediaSession Integration**: True background audio playback powered by AndroidX Media3 (`MediaSessionService`).
- **Lockscreen & Notification Panel Controls**:
  - Scrub the progress bar to seek paragraphs within the active chapter.
  - Forward / Backward skip buttons jump to the **Next Chapter** or **Previous Chapter**.
  - Remembers your exact paragraph position when jumping back and forth.
- **Headphone & Earbud Support**: Play, pause, and skip chapters using Bluetooth headphones, wired earbuds, or car audio controls.
- **Auto-Pausing & Resumption**: Handles audio focus transitions and headphone disconnections smoothly (prevents loudspeaker blaring).

### 🌐 Integrated Web Browser & Batch Chapter Downloader
- **In-Browser Live TTS**: Listen to web novel chapters directly on the web with synchronized paragraph highlighting and auto-scroll.
- **Interactive Reading**: Tap any paragraph on a webpage to seek TTS playback directly to that location.
- **Batch Chapter Downloader**: Automatically paginate, extract clean chapter text, and download entire web novels for offline reading.
- **Extensible Userscript Engine**: Prepared with a modular script injection architecture (`Userscript` interface, regex/wildcard URL pattern matching, custom DOM manipulation).
- **Multi-Tab Browsing**: Tab manager with history, bookmarks, and fast switching.

### ⏱️ Smart Sleep Timer
- Built-in sleep timer with presets (**15m, 20m, 30m, 45m, 1h**) and custom minute durations.
- **Intelligent Sleep Reset**: If playback pauses and you press play (from your earbuds or app), the timer automatically resets to your configured duration.

### 🧹 Advanced Content & Parsing Filters
Clean messy web novel scrapes, translator notes, and HTML boilerplate on the fly:
- **Quote- & CJK-Aware Sentence Chunking**: Seamlessly handles dialogue quotes (e.g. `"Stop!" she said.`) and CJK punctuation (`。`, `！`, `？`) without awkward sentence splits or skipped punctuation.
- **Auto Skip Header**: Define multiple keywords (e.g. `Chapter 1`, `Prologue`, `START`) to skip intro notes or links before the story begins.
- **Auto Skip Footer**: Define multiple keywords (e.g. `Next Chapter`, `Patreon`, `Comments`) to halt reading right before translator sign-offs.
- **HTML Content Selector**: Target specific story text elements (e.g., `#chapter-content`, `.entry-content`, `article`) via CSS selectors.
- **Auto Skip Words & Pronunciation Rules**: Strip watermarks or correct fantasy pronunciations with customizable phonetic replacements (defaults to exact word boundaries).
- Quick on/off master switches for all filter groups.

### 📱 Modern, Responsive Reader UI
- **Full-Width Popups**: Quick-settings and Content Filter dialogs adapt to the full screen width with zero cramped controls.
- **Themes**: **Sepia** (warm, easy on the eyes by default), **Dark**, and **Light**.
- **Speech Speed Control**: Fine-grained speed adjustments from **0.5x up to 2.5x**, with a quick speed cycler button on the dock (1.0x → 1.25x → 1.5x → 1.75x → 2.0x).
- **Voice Customization**: Select any installed system TTS voice language, override locale, and adjust pitch.
- **Clean Bottom Dock**: Compact progress metadata displaying `Ch. X / Total` and `¶ X / Total (%)`.

---

## 🏗️ Architecture & Technology Stack

- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material Design 3.
- **Audio & Media**: [AndroidX Media3](https://developer.android.com/guide/topics/media/media3) (`MediaSessionService`, `ExoPlayer`, `ForwardingPlayer`) orchestrated via a centralized `AudioPlaybackCoordinator`.
- **Text-to-Speech**: Hardened Android Native `TextToSpeech` engine with speech limits chunking, CJK autodetection, and `UtteranceProgressListener`.
- **Modular Browser Engine**: WebKit WebView with throttled `@JavascriptInterface` bridge, clean HTML extraction, and a dynamic `Userscript` injection engine.
- **Thread-Safe Atomic Storage**: Concurrency-safe persistence using `AtomicFile` and Kotlin Coroutine Mutexes, fully compliant with Android Scoped Storage (zero special permissions required).
- **HTML Parsing**: [Jsoup](https://jsoup.org/) for robust HTML parsing, cleaning, and CSS selector queries.
- **Architecture Pattern**: Decoupled Clean Architecture with MVVM, StateFlow, Coroutines, and centralized services.

---

## 🛠️ Building & Developing

### Prerequisites
- Android Studio Ladybug (or newer)
- JDK 17
- Android SDK 36 (minSdk 26, targetSdk 36)

### Build Commands
Clone the repository and build via Gradle:

```bash
# Clone repository
git clone https://github.com/faycalrafa/free-novel-reader-tts-android.git
cd free-novel-reader-tts-android/android-app

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew testDebugUnitTest
```

The output APK will be generated at:
`android-app/app/build/outputs/apk/release/app-release.apk`

