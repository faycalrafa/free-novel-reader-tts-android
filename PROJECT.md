# Project: NovelArrow Reader TTS — UX Refactor & Feature Expansion

## Architecture
NovelArrow Reader TTS follows Modern Android Architecture (MVVM) with Jetpack Compose (Material 3), AndroidX Media3, Native Android TTS, and a Userscript Web Engine.

Key architectural boundaries:
- **Navigation & Routing**: In `MainActivity.kt`, managed via `AppScreen` enum state and explicit `BackHandler`s.
- **Audio Subsystem (`playback`)**: `AudioPlaybackCoordinator` is the Single Source of Truth for all playback (TTS and audio files). `NovelAudioPlaybackService` hosts `MediaSessionService` and `ExoPlayer`. `TtsManager` handles speech synthesis and voice querying.
- **Browser Subsystem (`browser.engine` & `ui.browser`)**: `WebContentExtractor` handles DOM extraction and chapter parsing. `WebScriptInjector` handles JavaScript injection. `BrowserViewModel` manages state, download queues, and DOM verification.
- **Data Subsystem (`data`)**: `SettingsRepository` stores settings JSON with `Mutex` synchronization and atomic writes. `ChapterParser` parses text, filters active rules, and handles scoped pronunciation replacement.
- **UI Subsystem (`ui`)**: Jetpack Compose Material 3 screens (`GlobalSettingsScreen`, `PronunciationRulesScreen`, `SkipWordsScreen`, `VoiceEngineScreen`, `AudioStudioScreen`, `BrowserScreen`, `AboutScreen`).

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| F1 | Settings Navigation Routes | Deconstruct monolithic settings dialogs into dedicated routes (`SETTINGS_PRONUNCIATION`, `SETTINGS_SKIP_WORDS`, `SETTINGS_VOICE_ENGINE`, `SETTINGS_VOICES`) with discrete launch points and BackHandler navigation | M1 | ORIGINAL_REQUEST R1 |
| F2 | Pronunciation Rules Screen | Dedicated screen for listing, editing, toggling active/inactive, and deleting individual pronunciation rules with a master switch | M1 | ORIGINAL_REQUEST R1 |
| F3 | Scoped Pronunciation Matching | Add `PronunciationScopeType` (GLOBAL, FOLDER, URL_PATTERN) to `PronunciationRule`, and filter rules in `ChapterParser` based on novel folder and URL | M1 | ORIGINAL_REQUEST R1 |
| F4 | Responsive Dialog Layouts | Full-width dialogs with `FlowRow` for action buttons and preset chips to prevent squashing/truncation on narrow screens | M1 | ORIGINAL_REQUEST R1 |
| F5 | Voice Engine Route | Dedicated Voice Engine route replacing TTS voice dropdown with engine selection starting with Built-in Android TTS | M2 | ORIGINAL_REQUEST R2 |
| F6 | Language-Grouped Voice Picker & Audio Preview | Group voices by language/locale, display country and quality, and provide instant sample preview audio via `AudioPlaybackCoordinator` | M2 | ORIGINAL_REQUEST R2 |
| F7 | Audio Studio Library View | Default view shows generated folders and MP3 files; top-right `+` button navigates to Audio Generator view | M3 | ORIGINAL_REQUEST R3 |
| F8 | Unified Audio Playback | Extend `PlaybackSource` with `AUDIO_FILE`; tap MP3/folder to play via `AudioPlaybackCoordinator` and persistent bottom player `TtsDock` | M3 | ORIGINAL_REQUEST R3 |
| F9 | Granular Export Progress | Publish paragraph-level synthesis updates in `AudioExportEngine`, updating `AudioExportService` and UI with `Paragraph X / Total` and `Chapter Y / Total` | M3 | ORIGINAL_REQUEST R3 |
| F10 | Centered Web Download Overlay | Replace top banner with centered modal progress overlay `CenteredDownloadProgressCard` with dark scrim and stop control | M4 | ORIGINAL_REQUEST R4 |
| F11 | Dynamic DOM Content Verification | Add previous chapter heading and body signatures with polling verification in `BrowserViewModel` before parsing next chapter | M4 | ORIGINAL_REQUEST R4 |
| F12 | About Screen Launcher Icon | Render app launcher icon (`@mipmap/ic_launcher`) above the "BookReader" title on `AboutScreen` | M5 | ORIGINAL_REQUEST R5 |
| F13 | README Personal Hobby Framing | Update `README.md` to remove FOSS/license badges and frame as a personal hobby project by Faycal Rafa | M5 | ORIGINAL_REQUEST R5 |
| F14 | Version Bumping & Signing Integrity | Increment `versionCode` (20 -> 21) and `versionName` (3.0.1 -> 3.1.0) across `build.gradle.kts`, `release.yml`, and `AppUpdateManager.kt`; maintain release keystore | M6 | ORIGINAL_REQUEST R6 |
| F15 | Comprehensive Unit Tests & Verification | Implement unit tests for scoped rules, voice grouping, library scanning, granular progress, and DOM verification; pass `./gradlew testDebugUnitTest` | M6 | ORIGINAL_REQUEST R6 |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Settings Architecture & Pronunciation Rules | F1, F2, F3, F4 | none | DONE |
| M2 | Voice Engine & Voice Selection Routes | F5, F6 | none | DONE |
| M3 | Audio Studio Overhaul & Granular Progress | F7, F8, F9 | M2 | DONE |
| M4 | Web Multi-Download & Dynamic Content Detection | F10, F11 | none | PLANNED |
| M5 | About Screen & Branding | F12, F13 | none | PLANNED |
| M6 | Quality, Lifecycle Standards & Full Test Verification | F14, F15 | M1, M2, M3, M4, M5 | PLANNED |

## Interface Contracts

### 1. Pronunciation Rule Scoping (`Models.kt` & `ChapterParser.kt`)
```kotlin
enum class PronunciationScopeType {
    GLOBAL,
    FOLDER,
    URL_PATTERN
}

@Serializable
data class PronunciationRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val from: String,
    val to: String,
    val exactMatch: Boolean = true,
    val enabled: Boolean = true,
    val scopeType: PronunciationScopeType = PronunciationScopeType.GLOBAL,
    val scopePattern: String = ""
)

// ChapterParser signature extension
fun parse(
    text: String,
    chapterTitle: String = "",
    minSentenceLength: Int = 1,
    pronunciationRules: List<PronunciationRule> = emptyList(),
    pronunciationRulesEnabled: Boolean = true,
    skipWords: List<SkipWord> = emptyList(),
    skipWordsEnabled: Boolean = true,
    folderPath: String? = null,
    novelUrl: String? = null
): List<String>
```

### 2. Voice Audio Preview (`AudioPlaybackCoordinator.kt` & `TtsManager.kt`)
```kotlin
// AudioPlaybackCoordinator methods
val previewingVoiceName: StateFlow<String?>
fun previewVoice(voice: Voice, sampleText: String? = null)
fun stopVoicePreview()

// TtsManager methods
fun speakPreview(voice: Voice, sampleText: String)
fun stopPreview()
```

### 3. Audio File Playback (`AudioPlaybackCoordinator.kt` & `NovelAudioPlaybackService.kt`)
```kotlin
enum class PlaybackSource {
    NONE,
    READER,
    BROWSER,
    AUDIO_FILE
}

// AudioPlaybackCoordinator methods
fun playAudioFile(file: File, playlist: List<File> = listOf(file), index: Int = 0)
fun playAudioFolder(folder: File)
```

### 4. Granular MP3 Export Progress (`AudioExportEngine.kt` & `AudioExportService.kt`)
```kotlin
data class Running(
    val title: String,
    val percent: Int,
    val statusText: String,
    val currentChapter: Int = 0,
    val totalChapters: Int = 0,
    val currentParagraph: Int = 0,
    val totalParagraphs: Int = 0
) : ExportState()

// AudioExportEngine progress callback
typealias GranularProgressCallback = (
    currentChapter: Int,
    totalChapters: Int,
    currentParagraph: Int,
    totalParagraphs: Int,
    percent: Int,
    statusText: String
) -> Unit
```

### 5. Dynamic DOM Transition Detection (`BrowserViewModel.kt`)
```kotlin
// BrowserViewModel state & signatures
private var lastExtractedHeading: String = ""
private var lastExtractedSample: String = ""
// Polling interval 600ms, timeout 15000ms
```

## Code Layout
- `android-app/app/src/main/java/com/novelarrow/reader/`:
  - `MainActivity.kt`: AppScreen navigation, drawer bindings, BackHandlers.
  - `data/model/Models.kt`: Data classes (`PronunciationRule`, `SkipWord`, `PronunciationScopeType`).
  - `data/repository/SettingsRepository.kt`: CRUD operations for rules and skip words.
  - `data/parser/ChapterParser.kt`: Text parsing, rule filtering by scope and active state.
  - `playback/TtsManager.kt`: Speech synthesis, voice querying, sample preview.
  - `playback/AudioPlaybackCoordinator.kt`: Audio SSOT, voice preview, audio file/folder playback.
  - `playback/NovelAudioPlaybackService.kt`: ExoPlayer file playback, MediaSession notification.
  - `data/export/AudioExportEngine.kt`: Granular chapter/paragraph WAV synthesis.
  - `data/export/AudioExportService.kt`: Export foreground service state.
  - `data/export/AudioLibraryScanner.kt`: Discovering and listing generated MP3 files/folders.
  - `ui/settings/GlobalSettingsScreen.kt`: Settings navigation hub.
  - `ui/settings/PronunciationRulesScreen.kt`: Dedicated pronunciation rules management screen.
  - `ui/settings/SkipWordsScreen.kt`: Dedicated skip words management screen.
  - `ui/settings/VoiceEngineScreen.kt`: Dedicated voice engine and voice selection screen.
  - `ui/studio/AudioStudioScreen.kt`: Audio studio with library view, generator view, and bottom player bar.
  - `ui/browser/BrowserScreen.kt`: Centered download progress overlay.
  - `ui/browser/BrowserViewModel.kt`: Download loop with dynamic DOM transition detection.
  - `ui/about/AboutScreen.kt`: Launcher icon rendering.
  - `ui/common/UnifiedAudioSettingsDialog.kt`: FlowRow buttons, full width responsiveness.
- `android-app/app/build.gradle.kts`: Version bump (versionCode = 21, versionName = "3.1.0").
- `.github/workflows/release.yml`: Release tag and version update.
- `android-app/app/src/main/java/com/novelarrow/reader/data/updater/AppUpdateManager.kt`: Version fallback update.
- `README.md`: Personal hobby project framing.
