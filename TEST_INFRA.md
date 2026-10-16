# Test Infrastructure & Verification Plan: NovelArrow Reader TTS

## Test Philosophy
- Requirement-driven verification derived from ORIGINAL_REQUEST.md and GEMINI.md.
- Opaque-box and unit-level coverage for all new subsystems: Settings, Pronunciation Rules & Scoping, Voice Engine, Audio Studio & File Playback, Granular Export Progress, Centered Download Progress & DOM Content Verification, About & Branding, and Quality & Version Lifecycle.

## Feature Inventory Test Coverage
| # | Feature | Test Strategy & Verification Target | Verification Target |
|---|---------|--------------------------------------|---------------------|
| F1 | Settings Navigation Routes | Verify AppScreen routes for settings, BackHandler behavior, drawer integration | Unit & Compose Tests |
| F2 | Pronunciation Rules Screen | Verify CRUD operations in SettingsRepository and model serialization | Unit Test (`PronunciationRuleTest`) |
| F3 | Scoped Pronunciation Matching | Verify active/inactive toggle, FOLDER scope matching, URL_PATTERN scope matching | Unit Test (`ChapterParserTest`) |
| F4 | Responsive Dialog Layouts | Verify full width dialog properties and FlowRow wrapping | Static analysis & UI Review |
| F5 | Voice Engine Route | Verify engine selection list and navigation to voice picker | Unit Test |
| F6 | Language-Grouped Voice Picker & Preview | Verify grouping voices by locale, sample preview phrase generation | Unit Test (`VoiceGroupingTest`) |
| F7 | Audio Studio Library View | Verify file discovery in Downloads/BookReader and fallback directory | Unit Test (`AudioLibraryScannerTest`) |
| F8 | Unified Audio Playback | Verify PlaybackSource.AUDIO_FILE, file/folder playlist queueing in AudioPlaybackCoordinator | Unit Test |
| F9 | Granular Export Progress | Verify monotonic percentage progression across chapters and paragraphs | Unit Test (`AudioExportGranularProgressTest`) |
| F10 | Centered Web Download Overlay | Verify modal overlay structure, scrim, and progress calculation | Static analysis & UI Review |
| F11 | Dynamic DOM Content Verification | Verify heading and sample comparison logic, duplicate DOM prevention | Unit Test (`DomContentVerificationTest`) |
| F12 | About Screen Launcher Icon | Verify launcher icon resource binding | Static analysis |
| F13 | README Personal Hobby Framing | Verify absence of FOSS / Apache 2.0 license callouts | Forensic audit check |
| F14 | Version Bumping & Signing Integrity | Verify versionCode=21, versionName=3.1.0 in build.gradle.kts, release.yml, AppUpdateManager.kt, and release keystore | Audit & Static check |
| F15 | Comprehensive Unit Test Pass | Execute `./gradlew testDebugUnitTest` and compile sources | Automated Gradle Verification |

## Verification Commands
1. Unit Tests:
   `cd /workspaces/free-novel-reader-tts-android/android-app && ./gradlew testDebugUnitTest`
2. Source Compilation:
   `cd /workspaces/free-novel-reader-tts-android/android-app && ./gradlew compileDebugSources`
