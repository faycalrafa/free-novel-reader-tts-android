package com.novelarrow.reader.ui.reader

import android.speech.tts.Voice
import com.novelarrow.reader.data.backup.ExistingConfigsReport
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.data.model.ReaderSettings
import com.novelarrow.reader.data.model.RuleImpact
import com.novelarrow.reader.data.updater.SignatureCompatibility
import com.novelarrow.reader.data.updater.UpdateInfo
import java.io.File

data class ReaderUiState(
    val novels: List<Novel> = emptyList(),
    val archivedNovels: List<Novel> = emptyList(),
    val currentNovel: Novel? = null,
    val inspectedNovel: Novel? = null,
    val chapters: List<Chapter> = emptyList(),
    val inspectedChapters: List<Chapter> = emptyList(),
    val currentChapter: Chapter? = null,
    val paragraphs: List<Paragraph> = emptyList(),
    val currentParagraphIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isLibraryScanning: Boolean = false,
    val scanProgress: Float = 0f,
    val scanCurrentIndex: Int = 0,
    val scanTotalItems: Int = 0,
    val scanCurrentItem: String = "",
    val settings: ReaderSettings = ReaderSettings(),
    val isTtsDockVisible: Boolean = false,
    val isAutoLoopActive: Boolean = false,
    val loopRemaining: Int = 0,
    val availableVoices: List<Voice> = emptyList(),
    val sleepTimerMinutes: Int = 0,
    val sleepTimerRemainingSeconds: Int = 0,
    val toastMessage: String? = null,
    val updateInfo: UpdateInfo? = null,
    val showUpdateDialog: Boolean = false,
    val isCheckingUpdate: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val updateProgress: Float = 0f,
    val updateDownloadedBytes: Long = 0L,
    val updateTotalBytes: Long = 0L,
    val downloadedApk: File? = null,
    val installedVersionName: String = "2.9.0",
    val showRawEditor: Boolean = false,
    val rawChapterContent: String? = null,
    val showSignatureMismatchDialog: Boolean = false,
    val signatureMismatchInfo: SignatureCompatibility.Mismatch? = null,
    val showExistingConfigsDialog: Boolean = false,
    val existingConfigsReport: ExistingConfigsReport? = null,
    val lastActiveNovelId: String? = null,
    val flashHandleRed: Boolean = false,
    val activeRuleImpacts: List<RuleImpact> = emptyList()
)
