<div align="center">

  <!-- Animated Banner: Replace "./assets/banner.webp" with your repo file path or drag-and-drop your animated WebP/GIF here in the GitHub editor -->
  <a href="https://faycalrafa.github.io/projects/bookreader">
    <img src="./banner.webp" alt="BookReader — Offline Novel Reader & Multi-Engine TTS Banner" width="100%" style="max-width: 720px; border-radius: 16px;" />
  </a>

  <br /><br />

  <h1>📖 BookReader — Offline Novel Reader & TTS Engine</h1>
  <p>
    <strong>A flagship native Android web-novel reader engineered with Jetpack Compose (Material 3), tactile Claymorphic UI, offline-first synchronization, and a zero-thread-collision Media3 / Multi-Engine Text-to-Speech pipeline.</strong>
  </p>

  <!-- Badges -->
  <p>
    <a href="https://faycalrafa.github.io/projects/bookreader">
      <img src="https://img.shields.io/badge/🌐_Interactive_Showcase_&_Case_Study-faycalrafa.github.io-2563eb?style=for-the-badge" alt="Interactive Portfolio Showcase" />
    </a>
    <a href="https://github.com/faycalrafa/free-novel-reader-tts-android/releases">
      <img src="https://img.shields.io/badge/🤖_Download_APK-GitHub_Releases-16a34a?style=for-the-badge" alt="Download APK" />
    </a>
  </p>

  <p>
    <img src="https://img.shields.io/badge/Kotlin-Coroutines_&_StateFlow-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" />
    <img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
    <img src="https://img.shields.io/badge/AndroidX_Media3-ExoPlayer_&_MediaSession-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Media3" />
    <img src="https://img.shields.io/badge/Target_SDK-36-0f172a?style=flat-square" alt="Target SDK 36" />
    <img src="https://img.shields.io/badge/Architecture-Offline--First_•_Clean_MVVM-f59e0b?style=flat-square" alt="Architecture" />
  </p>

  <p>
    <a href="https://faycalrafa.github.io/projects/bookreader"><strong>✨ Try the In-Browser TTS Simulator & Read the Architectural Deep-Dive →</strong></a>
  </p>

</div>

---

## 🎯 Overview & Engineering Vision

Most mobile web-novel readers suffer from three critical flaws: fragile background audio that dies when the screen locks, robotic unsegmented TTS playback that ignores dialogue quotes, and database corruption during high-frequency chapter scraping.

**BookReader** solves these from first principles. Built natively for Android using **Kotlin** and **Jetpack Compose Material 3** with custom claymorphic tactile surfaces, it pairs an **offline-first HTML parser (`Jsoup`)** with a rock-solid **`MediaSessionService` + `ExoPlayer` + `Native TextToSpeech`** pipeline for uninterrupted audiobook-grade listening.

> 💡 **Want to test the TTS chunking engine right in your browser?**  
> Check out the live interactive showcase and full system breakdown on my portfolio:  
> 👉 **[https://faycalrafa.github.io/projects/bookreader](https://faycalrafa.github.io/projects/bookreader)**

---

## ✨ Key Architectural Highlights

* 🎧 **Zero-Thread-Collision Background Audio (`AndroidX Media3`):**
  * Full `MediaSessionService` integration providing native lockscreen controls, Bluetooth/headset media button routing, and persistent notification playback.
  * Strict main/background thread coordination between `ExoPlayer` audio focus and Android's native `TextToSpeech` synthesizer—eliminating race conditions and wake-lock drops.
* 🗣️ **Quote-Aware & CJK Multi-Engine TTS Segmentation:**
  * Custom regex & AST-style sentence chunker that intelligently detects dialogue quotes (`"..."`, `“...”`, `«...»`) and CJK punctuation boundaries.
  * Dynamically adjusts pitch, cadence, and pause intervals between narrative prose and spoken character dialogue across multiple installed TTS engines.
* 🛡️ **Crash-Safe Offline Storage (`AtomicFile` + Coroutine Mutex):**
  * Scoped Storage persistence guarded by Kotlin Coroutine `Mutex` locks and Android's `AtomicFile` write-ahead swap mechanism—guaranteeing zero JSON/state corruption even if the OS kills the process mid-sync.
* 🌐 **Resilient Web Novel Scraper (`Jsoup`):**
  * Smart DOM extraction engine that strips ads, navigation clutter, and malformed inline scripts while preserving clean semantic paragraphs for offline reading.
* 🎨 **Custom Claymorphic & Material 3 UI (`Jetpack Compose`):**
  * Smooth 60/120fps reactive reader interface featuring customizable typography, eye-care sepia/AMOLED dark themes, real-time sentence highlighting synchronized with TTS playback, and tactile claymorphic iconography.

---

## 🏗️ System Architecture & Audio Pipeline

```mermaid
flowchart TB
    subgraph Ingestion["1. Content Ingestion & Offline Persistence"]
        A["Web Novel Source / Local EPUB-HTML"] -->|Jsoup DOM Sanitizer| B["Clean Chapter Parser"]
        B -->|Coroutine Mutex Lock| C[("Scoped Storage\n(AtomicFile Swap)")]
    end

    subgraph Segmentation["2. Intelligent Text Segmentation"]
        C --> D["Quote-Aware & CJK\nSentence Chunker"]
        D --> E["Utterance Queue\n(Narrative vs. Dialogue Metadata)"]
    end

    subgraph MediaEngine["3. Background Audio & Media3 Service"]
        E --> F["Native TextToSpeech Engine\n(Multi-Voice Synthesis)"]
        F <-->|Zero-Collision Coordination| G["AndroidX Media3\n(MediaSessionService & ExoPlayer)"]
        G --> H["Lockscreen Controls • Bluetooth Media Keys • Audio Focus"]
    end

    subgraph UI["4. Reactive Presentation Layer"]
        E & G -->|Kotlin StateFlow| I["Jetpack Compose M3 UI\n(Real-Time Sentence Highlighting & Auto-Scroll)"]
    end
