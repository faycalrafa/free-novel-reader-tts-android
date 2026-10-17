package com.novelarrow.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.novelarrow.reader.data.updater.SignatureCompatibility
import com.novelarrow.reader.playback.QuickToolbarNotificationManager
import com.novelarrow.reader.ui.about.DevLogsScreen
import com.novelarrow.reader.ui.onboarding.OnboardingSlideshow
import com.novelarrow.reader.util.AppErrorBus
import com.novelarrow.reader.util.PermissionUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelarrow.reader.playback.NovelAudioPlaybackService
import com.novelarrow.reader.theme.BookReaderTheme
import com.novelarrow.reader.ui.about.AboutScreen
import com.novelarrow.reader.ui.archive.ArchiveScreen
import com.novelarrow.reader.ui.browser.BrowserScreen
import com.novelarrow.reader.ui.browser.BrowserViewModel
import com.novelarrow.reader.ui.common.UnifiedAudioSettingsDialog
import com.novelarrow.reader.ui.reader.LibraryDrawerContent
import com.novelarrow.reader.ui.reader.ReaderScreen
import com.novelarrow.reader.ui.reader.ReaderViewModel
import com.novelarrow.reader.ui.reader.UpdateAvailableDialog
import com.novelarrow.reader.ui.reader.UpdateDownloadingDialog
import com.novelarrow.reader.playback.AudioExportService
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.data.export.FileFusionManager
import com.novelarrow.reader.ui.about.ChangelogScreen
import com.novelarrow.reader.ui.common.IncomingLinkShareDialog
import com.novelarrow.reader.ui.common.IncomingTextShareDialog
import com.novelarrow.reader.ui.settings.BrowserIgnoredSelectorsScreen
import com.novelarrow.reader.ui.settings.BrowserNextSelectorScreen
import com.novelarrow.reader.ui.settings.ContentSelectorScreen
import com.novelarrow.reader.ui.settings.GlobalSettingsScreen
import com.novelarrow.reader.ui.settings.HardwareButtonsScreen
import com.novelarrow.reader.ui.settings.HeaderFooterSkipsScreen
import com.novelarrow.reader.ui.settings.PronunciationRulesScreen
import com.novelarrow.reader.ui.settings.SkipWordsScreen
import com.novelarrow.reader.ui.settings.ToolbarSettingsScreen
import com.novelarrow.reader.ui.settings.VoiceEngineScreen
import com.novelarrow.reader.ui.studio.AudioStudioScreen
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen {
    READER,
    ABOUT,
    BROWSER,
    SETTINGS,
    TOOLBAR_SETTINGS,
    HARDWARE_BUTTON_SETTINGS,
    SETTINGS_PRONUNCIATION,
    SETTINGS_SKIP_WORDS,
    SETTINGS_HEADER_FOOTER_SKIPS,
    SETTINGS_CONTENT_SELECTOR,
    SETTINGS_BROWSER_IGNORED_SELECTORS,
    SETTINGS_BROWSER_NEXT_SELECTOR,
    VOICE_ENGINE,
    DEV_LOGS,
    ARCHIVE,
    CHANGELOG,
    TEXT_TO_MP3
}

class MainActivity : ComponentActivity() {
    private val openCssSelectorEvent = mutableStateOf(false)
    private val sharedUrlEvent = mutableStateOf<String?>(null)
    private val sharedTextEvent = mutableStateOf<String?>(null)

    companion object {
        const val EXTRA_RESUME_PLAYBACK = "extra_resume_playback"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkCssSelectorIntent(intent)
        handleIncomingShareIntent(intent)
        if (intent.getBooleanExtra(EXTRA_RESUME_PLAYBACK, false)) {
            AudioPlaybackCoordinator.restoreLastHearingSession(autoPlay = true)
        }
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            if (!text.isNullOrBlank()) {
                if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) {
                    sharedUrlEvent.value = text
                } else {
                    sharedTextEvent.value = text
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
        val hasCompletedOnboarding = prefs.getBoolean("has_completed_onboarding", false)

        if (hasCompletedOnboarding && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        checkCssSelectorIntent(intent)
        handleIncomingShareIntent(intent)

        setContent {
            val viewModel: ReaderViewModel = viewModel()
            val browserViewModel: BrowserViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()
            val browserUiState by browserViewModel.uiState.collectAsState()
            var currentScreen by rememberSaveable { mutableStateOf(AppScreen.READER) }
            var browserSettingsSourceScreen by rememberSaveable { mutableStateOf(AppScreen.SETTINGS) }
            val context = LocalContext.current
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            val prefs = remember { context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE) }
            var isOnboardingCompleted by rememberSaveable {
                mutableStateOf(prefs.getBoolean("has_completed_onboarding", false))
            }

            LaunchedEffect(Unit) {
                // Pinned quick toolbar initialization on boot
                QuickToolbarNotificationManager.updateToolbar(context)
                if (intent?.getBooleanExtra(EXTRA_RESUME_PLAYBACK, false) == true) {
                    AudioPlaybackCoordinator.restoreLastHearingSession(autoPlay = true)
                }
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.onAppResumed()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            LaunchedEffect(Unit) {
                AudioExportService.directShareEvent.collect { req ->
                    try {
                        val shareIntent = FileFusionManager.createShareIntent(this@MainActivity, req.file, req.mimeType)
                        startActivity(Intent.createChooser(shareIntent, "Share ${req.title}"))
                    } catch (e: Exception) {
                        AppLogger.e("MainActivity", "Failed to launch direct share chooser: ${e.message}", e)
                    }
                }
            }

            LaunchedEffect(isOnboardingCompleted) {
                if (isOnboardingCompleted) {
                    if (PermissionUtils.hasStoragePermission(context)) {
                        viewModel.checkAndImportDefaultDownloadFolderOnFirstLaunch()
                        val hasHandledConfigs = prefs.getBoolean("has_handled_existing_configs", false)
                        if (!hasHandledConfigs) {
                            viewModel.checkForExistingConfigs()
                        }
                    }
                }
            }

            val shouldOpenCssSelector by openCssSelectorEvent
            var showCssSelectorDialog by remember { mutableStateOf(false) }
            var showGlobalAudioSettings by remember { mutableStateOf(false) }

            LaunchedEffect(shouldOpenCssSelector) {
                if (shouldOpenCssSelector) {
                    showCssSelectorDialog = true
                    openCssSelectorEvent.value = false
                }
            }

            BackHandler(enabled = drawerState.isOpen) {
                scope.launch { drawerState.close() }
            }

            BackHandler(enabled = !drawerState.isOpen && currentScreen == AppScreen.TOOLBAR_SETTINGS) {
                currentScreen = AppScreen.SETTINGS
            }

            BackHandler(enabled = !drawerState.isOpen && currentScreen == AppScreen.HARDWARE_BUTTON_SETTINGS) {
                currentScreen = AppScreen.SETTINGS
            }

            BackHandler(enabled = !drawerState.isOpen && currentScreen == AppScreen.SETTINGS_PRONUNCIATION) {
                currentScreen = AppScreen.SETTINGS
            }

            BackHandler(enabled = !drawerState.isOpen && currentScreen == AppScreen.SETTINGS_SKIP_WORDS) {
                currentScreen = AppScreen.SETTINGS
            }

            BackHandler(enabled = !drawerState.isOpen && currentScreen == AppScreen.SETTINGS_HEADER_FOOTER_SKIPS) {
                currentScreen = AppScreen.SETTINGS
            }

            BackHandler(enabled = !drawerState.isOpen && currentScreen == AppScreen.SETTINGS_CONTENT_SELECTOR) {
                currentScreen = AppScreen.SETTINGS
            }

            BackHandler(enabled = !drawerState.isOpen && currentScreen == AppScreen.VOICE_ENGINE) {
                currentScreen = AppScreen.SETTINGS
            }

            BackHandler(enabled = !drawerState.isOpen && currentScreen == AppScreen.DEV_LOGS) {
                currentScreen = AppScreen.ABOUT
            }

            BackHandler(enabled = !drawerState.isOpen && currentScreen == AppScreen.CHANGELOG) {
                currentScreen = AppScreen.ABOUT
            }

            BackHandler(enabled = !drawerState.isOpen && (currentScreen == AppScreen.SETTINGS_BROWSER_IGNORED_SELECTORS || currentScreen == AppScreen.SETTINGS_BROWSER_NEXT_SELECTOR)) {
                currentScreen = browserSettingsSourceScreen
            }

            BackHandler(enabled = !drawerState.isOpen && (currentScreen == AppScreen.ABOUT || currentScreen == AppScreen.SETTINGS || currentScreen == AppScreen.ARCHIVE || currentScreen == AppScreen.TEXT_TO_MP3)) {
                currentScreen = AppScreen.READER
            }

            val folderPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                uri?.let { viewModel.scanRootFolder(it) }
            }

            val saveApkLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
            ) { uri ->
                uri?.let { destinationUri ->
                    viewModel.saveApkToCustomUri(destinationUri, context)
                }
            }

            LaunchedEffect(uiState.toastMessage) {
                uiState.toastMessage?.let { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    viewModel.clearToast()
                }
            }

            if (!isOnboardingCompleted) {
                BookReaderTheme(themeMode = uiState.settings.theme) {
                    OnboardingSlideshow(
                        onFinish = {
                            prefs.edit().putBoolean("has_completed_onboarding", true).apply()
                            isOnboardingCompleted = true
                            viewModel.checkAndImportDefaultDownloadFolderOnFirstLaunch()
                            val hasHandledConfigs = prefs.getBoolean("has_handled_existing_configs", false)
                            if (!hasHandledConfigs) {
                                viewModel.checkForExistingConfigs()
                            }
                        }
                    )
                }
            } else {
                BookReaderTheme(themeMode = uiState.settings.theme) {
                    ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = currentScreen != AppScreen.BROWSER,
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.width(320.dp)
                        ) {
                            val inspected = uiState.inspectedNovel
                            val activeDisplayNovel = inspected ?: uiState.currentNovel
                            val activeDisplayChapters = if (inspected != null && inspected.id != uiState.currentNovel?.id) {
                                uiState.inspectedChapters
                            } else {
                                uiState.chapters
                            }
                            val activeDisplayChapter = if (inspected == null || inspected.id == uiState.currentNovel?.id) {
                                uiState.currentChapter
                            } else null

                            LibraryDrawerContent(
                                novels = uiState.novels,
                                currentNovel = activeDisplayNovel,
                                chapters = activeDisplayChapters,
                                currentChapter = activeDisplayChapter,
                                browserTabs = browserUiState.tabs,
                                currentTabId = browserUiState.currentTabId,
                                installedVersionName = uiState.installedVersionName,
                                onSelectNovel = { novel ->
                                    viewModel.inspectNovel(novel)
                                },
                                onSelectChapter = { index ->
                                    viewModel.selectChapterFromInspected(index)
                                    currentScreen = AppScreen.READER
                                    scope.launch { drawerState.close() }
                                },
                                onDeleteNovel = { novel, deleteFromDisk ->
                                    viewModel.deleteNovel(novel.id, deleteFromDisk)
                                },
                                onDeleteChapter = { chapter, deleteFromDisk ->
                                    viewModel.deleteChapter(chapter, deleteFromDisk)
                                },
                                onMoveChapter = { from, to ->
                                    viewModel.moveChapter(from, to)
                                },
                                onSortChapters = { preset ->
                                    viewModel.sortChapters(preset)
                                },
                                onToggleChapterReadStatus = { chapter ->
                                    viewModel.toggleChapterReadStatus(chapter)
                                },
                                onAddFolder = { folderPickerLauncher.launch(null) },
                                onRefreshFolders = { viewModel.refreshLinkedFolders() },
                                onOpenBrowser = { createNewTab ->
                                    if (createNewTab) browserViewModel.openNewTab()
                                    currentScreen = AppScreen.BROWSER
                                    scope.launch { drawerState.close() }
                                },
                                onSelectTab = { tabId ->
                                    browserViewModel.selectTab(tabId)
                                    currentScreen = AppScreen.BROWSER
                                    scope.launch { drawerState.close() }
                                },
                                onNewTab = {
                                    browserViewModel.openNewTab()
                                    currentScreen = AppScreen.BROWSER
                                    scope.launch { drawerState.close() }
                                },
                                onCloseTab = { tabId ->
                                    browserViewModel.closeTab(tabId)
                                },
                                onOpenSettings = {
                                    currentScreen = AppScreen.SETTINGS
                                    scope.launch { drawerState.close() }
                                },
                                onOpenAbout = {
                                    currentScreen = AppScreen.ABOUT
                                    scope.launch { drawerState.close() }
                                },
                                onOpenDevLogs = {
                                    currentScreen = AppScreen.DEV_LOGS
                                    scope.launch { drawerState.close() }
                                },
                                onOpenAudioStudio = {
                                    currentScreen = AppScreen.TEXT_TO_MP3
                                    scope.launch { drawerState.close() }
                                },
                                onOpenArchive = {
                                    currentScreen = AppScreen.ARCHIVE
                                    scope.launch { drawerState.close() }
                                },
                                onArchiveNovel = { novel ->
                                    viewModel.archiveNovel(novel)
                                },
                                onCloseDrawer = { scope.launch { drawerState.close() } }
                            )
                        }
                    }
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (currentScreen) {
                            AppScreen.READER -> {
                                ReaderScreen(
                                    viewModel = viewModel,
                                    onNavigateToAbout = { currentScreen = AppScreen.ABOUT },
                                    onNavigateToBrowser = { createNewTab ->
                                        if (createNewTab) browserViewModel.openNewTab()
                                        currentScreen = AppScreen.BROWSER
                                    },
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onAddFolder = { folderPickerLauncher.launch(null) },
                                    onNavigateToVoiceEngine = { currentScreen = AppScreen.VOICE_ENGINE },
                                    onNavigateToPronunciationRules = { currentScreen = AppScreen.SETTINGS_PRONUNCIATION },
                                    onNavigateToSkipWords = { currentScreen = AppScreen.SETTINGS_SKIP_WORDS },
                                    onNavigateToHeaderFooterSkips = { currentScreen = AppScreen.SETTINGS_HEADER_FOOTER_SKIPS },
                                    onNavigateToContentSelector = { currentScreen = AppScreen.SETTINGS_CONTENT_SELECTOR },
                                    onOpenAudioStudio = { currentScreen = AppScreen.TEXT_TO_MP3 }
                                )
                            }
                            AppScreen.ABOUT -> {
                                AboutScreen(
                                    installedVersionName = uiState.installedVersionName,
                                    isCheckingUpdate = uiState.isCheckingUpdate,
                                    downloadedApk = uiState.downloadedApk,
                                    onCheckForUpdates = { viewModel.checkForUpdate(silent = false) },
                                    onInstallDownloadedApk = { viewModel.installDownloadedApk(context) },
                                    onSaveApkToPath = {
                                        val apkName = uiState.updateInfo?.apkName?.ifBlank { "update-latest.apk" } ?: "update-latest.apk"
                                        saveApkLauncher.launch(apkName)
                                    },
                                    onReplayOnboarding = {
                                        prefs.edit().putBoolean("has_handled_existing_configs", false).apply()
                                        isOnboardingCompleted = false
                                        currentScreen = AppScreen.READER
                                    },
                                    onNavigateToDevLogs = { currentScreen = AppScreen.DEV_LOGS },
                                    onNavigateToChangelog = { currentScreen = AppScreen.CHANGELOG },
                                    onNavigateBack = { currentScreen = AppScreen.READER },
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                            AppScreen.DEV_LOGS -> {
                                DevLogsScreen(
                                    installedVersionName = uiState.installedVersionName,
                                    onNavigateBack = { currentScreen = AppScreen.ABOUT },
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                            AppScreen.SETTINGS -> {
                                GlobalSettingsScreen(
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onNavigateToToolbarSettings = { currentScreen = AppScreen.TOOLBAR_SETTINGS },
                                    onNavigateToHardwareButtons = { currentScreen = AppScreen.HARDWARE_BUTTON_SETTINGS },
                                    onNavigateToVoiceEngine = { currentScreen = AppScreen.VOICE_ENGINE },
                                    onNavigateToPronunciationRules = { currentScreen = AppScreen.SETTINGS_PRONUNCIATION },
                                    onNavigateToSkipWords = { currentScreen = AppScreen.SETTINGS_SKIP_WORDS },
                                    onNavigateToHeaderFooterSkips = { currentScreen = AppScreen.SETTINGS_HEADER_FOOTER_SKIPS },
                                    onNavigateToContentSelector = {
                                        browserSettingsSourceScreen = AppScreen.SETTINGS
                                        currentScreen = AppScreen.SETTINGS_CONTENT_SELECTOR
                                    },
                                    onNavigateToBrowserIgnoredSelectors = {
                                        browserSettingsSourceScreen = AppScreen.SETTINGS
                                        currentScreen = AppScreen.SETTINGS_BROWSER_IGNORED_SELECTORS
                                    },
                                    onNavigateToBrowserNextSelector = {
                                        browserSettingsSourceScreen = AppScreen.SETTINGS
                                        currentScreen = AppScreen.SETTINGS_BROWSER_NEXT_SELECTOR
                                    },
                                    onClearAllData = { viewModel.clearAllData() },
                                    onOpenAudioSettings = { showGlobalAudioSettings = true },
                                    onNavigateToAbout = { currentScreen = AppScreen.ABOUT }
                                )
                            }
                            AppScreen.VOICE_ENGINE -> {
                                VoiceEngineScreen(
                                    onNavigateBack = { currentScreen = AppScreen.SETTINGS }
                                )
                            }
                            AppScreen.SETTINGS_PRONUNCIATION -> {
                                PronunciationRulesScreen(
                                    masterEnabled = uiState.settings.pronunciationRulesEnabled,
                                    rules = uiState.settings.pronunciationRules,
                                    onToggleMaster = { viewModel.updatePronunciationRulesEnabled(it) },
                                    onAddRule = { rule -> viewModel.addPronunciationRule(rule) },
                                    onUpdateRule = { rule -> viewModel.updatePronunciationRule(rule) },
                                    onToggleRule = { id, enabled -> viewModel.togglePronunciationRule(id, enabled) },
                                    onDeleteRule = { id -> viewModel.deletePronunciationRule(id) },
                                    onNavigateBack = { currentScreen = AppScreen.SETTINGS }
                                )
                            }
                            AppScreen.SETTINGS_SKIP_WORDS -> {
                                SkipWordsScreen(
                                    masterEnabled = uiState.settings.skipWordsEnabled,
                                    skipWords = uiState.settings.skipWords,
                                    onToggleMaster = { viewModel.updateSkipWordsEnabled(it) },
                                    onAddSkipWord = { word -> viewModel.addSkipWord(word) },
                                    onUpdateSkipWord = { word -> viewModel.updateSkipWord(word) },
                                    onToggleSkipWord = { id, enabled -> viewModel.toggleSkipWord(id, enabled) },
                                    onDeleteSkipWord = { id -> viewModel.deleteSkipWord(id) },
                                    onNavigateBack = { currentScreen = AppScreen.SETTINGS }
                                )
                            }
                            AppScreen.SETTINGS_HEADER_FOOTER_SKIPS -> {
                                HeaderFooterSkipsScreen(
                                    headerSkipEnabled = uiState.settings.headerSkipEnabled,
                                    footerSkipEnabled = uiState.settings.footerSkipEnabled,
                                    headerRules = uiState.settings.headerSkipRules,
                                    footerRules = uiState.settings.footerSkipRules,
                                    onToggleHeaderMaster = { viewModel.updateHeaderSkipEnabled(it) },
                                    onToggleFooterMaster = { viewModel.updateFooterSkipEnabled(it) },
                                    onAddHeaderRule = { rule -> viewModel.addHeaderSkipRule(rule) },
                                    onUpdateHeaderRule = { rule -> viewModel.updateHeaderSkipRule(rule) },
                                    onToggleHeaderRule = { id, enabled -> viewModel.toggleHeaderSkipRule(id, enabled) },
                                    onDeleteHeaderRule = { id -> viewModel.deleteHeaderSkipRule(id) },
                                    onAddFooterRule = { rule -> viewModel.addFooterSkipRule(rule) },
                                    onUpdateFooterRule = { rule -> viewModel.updateFooterSkipRule(rule) },
                                    onToggleFooterRule = { id, enabled -> viewModel.toggleFooterSkipRule(id, enabled) },
                                    onDeleteFooterRule = { id -> viewModel.deleteFooterSkipRule(id) },
                                    onNavigateBack = { currentScreen = AppScreen.SETTINGS }
                                )
                            }
                            AppScreen.SETTINGS_CONTENT_SELECTOR -> {
                                ContentSelectorScreen(
                                    masterEnabled = uiState.settings.contentSelectorEnabled,
                                    selectors = uiState.settings.contentSelectors,
                                    onToggleMaster = { viewModel.updateContentSelectorEnabled(it) },
                                    onAddSelector = { rule -> viewModel.addContentSelector(rule) },
                                    onUpdateSelector = { rule -> viewModel.updateContentSelector(rule) },
                                    onToggleSelector = { id, enabled -> viewModel.toggleContentSelector(id, enabled) },
                                    onDeleteSelector = { id -> viewModel.deleteContentSelector(id) },
                                    onNavigateBack = { currentScreen = browserSettingsSourceScreen }
                                )
                            }
                            AppScreen.SETTINGS_BROWSER_IGNORED_SELECTORS -> {
                                BrowserIgnoredSelectorsScreen(
                                    ignoredSelectors = browserUiState.settings.ignoredSelectors,
                                    onAddSelector = { browserViewModel.addIgnoredSelector(it) },
                                    onRemoveSelector = { browserViewModel.removeIgnoredSelector(it) },
                                    onResetDefaults = { browserViewModel.resetIgnoredSelectors() },
                                    onNavigateBack = { currentScreen = browserSettingsSourceScreen }
                                )
                            }
                            AppScreen.SETTINGS_BROWSER_NEXT_SELECTOR -> {
                                BrowserNextSelectorScreen(
                                    currentSelector = browserUiState.settings.nextSelector,
                                    onSaveSelector = { browserViewModel.updateNextSelector(it) },
                                    onNavigateBack = { currentScreen = browserSettingsSourceScreen }
                                )
                            }
                            AppScreen.TOOLBAR_SETTINGS -> {
                                ToolbarSettingsScreen(
                                    toolbarSettings = uiState.settings.quickToolbarSettings,
                                    onUpdateSettings = { viewModel.updateQuickToolbarSettings(it) },
                                    onNavigateBack = { currentScreen = AppScreen.SETTINGS },
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                            AppScreen.HARDWARE_BUTTON_SETTINGS -> {
                                HardwareButtonsScreen(
                                    settings = uiState.settings.hardwareButtonSettings,
                                    onUpdateSettings = { viewModel.updateHardwareButtonSettings(it) },
                                    onNavigateBack = { currentScreen = AppScreen.SETTINGS },
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                            AppScreen.BROWSER -> {
                                BrowserScreen(
                                    viewModel = browserViewModel,
                                    onNavigateBack = {
                                        viewModel.refreshLibrary()
                                        currentScreen = AppScreen.READER
                                    },
                                    onOpenDownloadedNovel = { novelId ->
                                        viewModel.refreshLibrary(novelId)
                                        currentScreen = AppScreen.READER
                                    },
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onNavigateToPronunciationRules = { currentScreen = AppScreen.SETTINGS_PRONUNCIATION },
                                    onNavigateToSkipWords = { currentScreen = AppScreen.SETTINGS_SKIP_WORDS },
                                    onNavigateToVoiceEngine = { currentScreen = AppScreen.VOICE_ENGINE },
                                    onNavigateToHeaderFooterSkips = { currentScreen = AppScreen.SETTINGS_HEADER_FOOTER_SKIPS },
                                    onNavigateToContentSelector = {
                                        browserSettingsSourceScreen = AppScreen.BROWSER
                                        currentScreen = AppScreen.SETTINGS_CONTENT_SELECTOR
                                    },
                                    onNavigateToNextSelector = {
                                        browserSettingsSourceScreen = AppScreen.BROWSER
                                        currentScreen = AppScreen.SETTINGS_BROWSER_NEXT_SELECTOR
                                    },
                                    onNavigateToIgnoredSelectors = {
                                        browserSettingsSourceScreen = AppScreen.BROWSER
                                        currentScreen = AppScreen.SETTINGS_BROWSER_IGNORED_SELECTORS
                                    }
                                )
                            }
                            AppScreen.ARCHIVE -> {
                                ArchiveScreen(
                                    archivedNovels = uiState.archivedNovels,
                                    onRestoreNovel = { novel ->
                                        viewModel.restoreNovel(novel)
                                    },
                                    onDeleteNovel = { novel, deleteFromDisk ->
                                        viewModel.deleteNovel(novel.id, deleteFromDisk)
                                    },
                                    onUpdateNovelTitle = { novelId, newTitle ->
                                        viewModel.updateNovelTitle(novelId, newTitle)
                                    },
                                    onGetChapters = { novelId ->
                                        viewModel.getArchivedChapters(novelId)
                                    },
                                    onDeleteChapter = { novelId, chapter, deleteFromDisk ->
                                        viewModel.deleteArchivedChapter(novelId, chapter, deleteFromDisk)
                                    },
                                    onNavigateBack = { currentScreen = AppScreen.READER },
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                            AppScreen.CHANGELOG -> {
                                ChangelogScreen(
                                    onNavigateBack = { currentScreen = AppScreen.ABOUT }
                                )
                            }
                            AppScreen.TEXT_TO_MP3 -> {
                                AudioStudioScreen(
                                    novels = uiState.novels,
                                    currentNovel = uiState.currentNovel,
                                    onNavigateBack = { currentScreen = AppScreen.READER },
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                        }

                        // System Update Dialogs available across all routes
                        if (uiState.showUpdateDialog && uiState.updateInfo != null) {
                            UpdateAvailableDialog(
                                updateInfo = uiState.updateInfo!!,
                                downloadedApk = uiState.downloadedApk,
                                onDownloadClick = { viewModel.startUpdateDownload() },
                                onInstallClick = { viewModel.installDownloadedApk(context) },
                                onSaveToPathClick = {
                                    val apkName = uiState.updateInfo?.apkName?.ifBlank { "update-latest.apk" } ?: "update-latest.apk"
                                    saveApkLauncher.launch(apkName)
                                },
                                onDismiss = { viewModel.dismissUpdateDialog() }
                            )
                        }

                        if (uiState.isDownloadingUpdate) {
                            UpdateDownloadingDialog(
                                progress = uiState.updateProgress,
                                downloadedBytes = uiState.updateDownloadedBytes,
                                totalBytes = uiState.updateTotalBytes,
                                onCancel = { viewModel.cancelUpdateDownload() }
                            )
                        }

                        if (uiState.showSignatureMismatchDialog) {
                            SignatureMismatchDialog(
                                mismatchInfo = uiState.signatureMismatchInfo,
                                downloadedApk = uiState.downloadedApk,
                                onSaveToPathClick = {
                                    val apkName = uiState.updateInfo?.apkName?.ifBlank { "update-latest.apk" } ?: "update-latest.apk"
                                    saveApkLauncher.launch(apkName)
                                },
                                onForceInstallClick = {
                                    viewModel.dismissSignatureMismatchDialog()
                                    viewModel.installDownloadedApk(context, force = true)
                                },
                                onDismiss = { viewModel.dismissSignatureMismatchDialog() }
                            )
                        }

                        if (showCssSelectorDialog) {
                            NotificationCssSelectorDialog(
                                initialSelector = uiState.settings.contentSelector,
                                isEnabled = uiState.settings.contentSelectorEnabled,
                                onSave = { selector, enabled ->
                                    viewModel.updateContentSelector(selector)
                                    viewModel.updateContentSelectorEnabled(enabled)
                                    browserViewModel.updateContentSelector(selector)
                                    Toast.makeText(context, "CSS content selector updated", Toast.LENGTH_SHORT).show()
                                },
                                onDismiss = { showCssSelectorDialog = false }
                            )
                        }

                        if (showGlobalAudioSettings) {
                            UnifiedAudioSettingsDialog(
                                speechRate = uiState.settings.speechRate,
                                speechPitch = uiState.settings.speechPitch,
                                selectedVoiceName = uiState.settings.selectedVoiceName,
                                availableVoices = uiState.availableVoices,
                                sleepTimerMinutes = uiState.sleepTimerMinutes,
                                sleepTimerRemainingSeconds = uiState.sleepTimerRemainingSeconds,
                                pronunciationRulesEnabled = uiState.settings.pronunciationRulesEnabled,
                                pronunciationRules = uiState.settings.pronunciationRules,
                                skipWordsEnabled = uiState.settings.skipWordsEnabled,
                                skipWords = uiState.settings.skipWords,
                                headerSkipRulesEnabled = uiState.settings.headerSkipEnabled,
                                headerSkipRules = uiState.settings.headerSkipRules,
                                footerSkipRulesEnabled = uiState.settings.footerSkipEnabled,
                                footerSkipRules = uiState.settings.footerSkipRules,
                                contentSelectorEnabled = uiState.settings.contentSelectorEnabled,
                                contentSelector = uiState.settings.contentSelector,
                                onSpeechRateChange = { viewModel.updateSpeechRate(it) },
                                onSpeechPitchChange = { viewModel.updateSpeechPitch(it) },
                                onVoiceChange = { viewModel.updateVoice(it) },
                                onSleepTimerChange = { viewModel.setSleepTimer(it) },
                                onPronunciationRulesEnabledChange = { viewModel.updatePronunciationRulesEnabled(it) },
                                onAddPronunciationRule = { from, to, exact -> viewModel.addPronunciationRule(from, to, exact) },
                                onRemovePronunciationRule = { viewModel.removePronunciationRule(it) },
                                onSkipWordsEnabledChange = { viewModel.updateSkipWordsEnabled(it) },
                                onAddSkipWord = { word, exact -> viewModel.addSkipWord(word, exact) },
                                onRemoveSkipWord = { viewModel.removeSkipWord(it) },
                                onHeaderSkipRulesEnabledChange = { viewModel.updateHeaderSkipEnabled(it) },
                                onAddHeaderSkipRule = { text, exact -> viewModel.addHeaderSkipRule(text, exact) },
                                onRemoveHeaderSkipRule = { viewModel.removeHeaderSkipRule(it) },
                                onFooterSkipRulesEnabledChange = { viewModel.updateFooterSkipEnabled(it) },
                                onAddFooterSkipRule = { text, exact -> viewModel.addFooterSkipRule(text, exact) },
                                onRemoveFooterSkipRule = { viewModel.removeFooterSkipRule(it) },
                                onContentSelectorEnabledChange = { viewModel.updateContentSelectorEnabled(it) },
                                onContentSelectorChange = { viewModel.updateContentSelector(it) },
                                onNavigateToPronunciationRules = {
                                    showGlobalAudioSettings = false
                                    currentScreen = AppScreen.SETTINGS_PRONUNCIATION
                                },
                                onNavigateToSkipWords = {
                                    showGlobalAudioSettings = false
                                    currentScreen = AppScreen.SETTINGS_SKIP_WORDS
                                },
                                onNavigateToVoiceEngine = {
                                    showGlobalAudioSettings = false
                                    currentScreen = AppScreen.VOICE_ENGINE
                                },
                                onNavigateToHeaderFooterSkips = {
                                    showGlobalAudioSettings = false
                                    currentScreen = AppScreen.SETTINGS_HEADER_FOOTER_SKIPS
                                },
                                onNavigateToContentSelector = {
                                    showGlobalAudioSettings = false
                                    currentScreen = AppScreen.SETTINGS_CONTENT_SELECTOR
                                },
                                onDismiss = { showGlobalAudioSettings = false }
                            )
                        }

                        if (uiState.showExistingConfigsDialog && uiState.existingConfigsReport != null) {
                            val report = uiState.existingConfigsReport!!
                            AlertDialog(
                                onDismissRequest = { viewModel.skipExistingConfigs() },
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                                        Text("Restore Previous Data?")
                                    }
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "Found saved preferences and reading progress from a previous installation:",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                report.summaryText,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                        Text(
                                            "Would you like to restore your preferences and previously loaded novel folders, or start fresh and delete them?",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                confirmButton = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                prefs.edit().putBoolean("has_handled_existing_configs", true).apply()
                                                viewModel.restoreExistingConfigs(report)
                                            },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Restore Data")
                                        }
                                        OutlinedButton(
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                prefs.edit().putBoolean("has_handled_existing_configs", true).apply()
                                                viewModel.refuseAndDeleteExistingConfigs(report)
                                            },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Delete & Start Fresh", color = MaterialTheme.colorScheme.error)
                                        }
                                        TextButton(
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                prefs.edit().putBoolean("has_handled_existing_configs", true).apply()
                                                viewModel.skipExistingConfigs()
                                            }
                                        ) {
                                            Text("Skip for Now")
                                        }
                                    }
                                },
                                dismissButton = null
                            )
                        }

                        val currentAppError by AppErrorBus.currentError.collectAsState()
                        if (currentAppError != null) {
                            val err = currentAppError!!
                            AlertDialog(
                                onDismissRequest = { AppErrorBus.dismiss() },
                                shape = RoundedCornerShape(16.dp),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(32.dp)
                                    )
                                },
                                title = {
                                    Text(
                                        text = err.title,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                },
                                text = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = err.message,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (!err.stackTrace.isNullOrBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 140.dp)
                                            ) {
                                                SelectionContainer(modifier = Modifier.padding(8.dp)) {
                                                    LazyColumn {
                                                        item {
                                                            Text(
                                                                text = err.stackTrace,
                                                                fontFamily = FontFamily.Monospace,
                                                                fontSize = 11.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText(
                                                    "BookReader Error Log",
                                                    "ERROR: ${err.title}\nMessage: ${err.message}\n\nStackTrace:\n${err.stackTrace.ifBlank { "N/A" }}"
                                                )
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "Error log copied to clipboard", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                                            Text("Copy Error Log")
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    AppErrorBus.dismiss()
                                                    currentScreen = AppScreen.DEV_LOGS
                                                },
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text("Open Dev Logs")
                                            }
                                            TextButton(
                                                modifier = Modifier.weight(1f),
                                                onClick = { AppErrorBus.dismiss() }
                                            ) {
                                                Text("Dismiss")
                                            }
                                        }
                                    }
                                },
                                dismissButton = null
                            )
                        }

                        val sharedUrl by sharedUrlEvent
                        if (sharedUrl != null) {
                            IncomingLinkShareDialog(
                                url = sharedUrl!!,
                                onDismiss = { sharedUrlEvent.value = null },
                                onOpenInBrowser = { url ->
                                    browserViewModel.openNewTab(url)
                                    currentScreen = AppScreen.BROWSER
                                },
                                onSaveFrozenHtml = { url ->
                                    browserViewModel.openNewTab(url)
                                    currentScreen = AppScreen.BROWSER
                                    Toast.makeText(context, "Opening page to save frozen HTML", Toast.LENGTH_SHORT).show()
                                },
                                onSaveAsMp3 = { url, speed ->
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                                            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/109.0 Firefox/114.0")
                                            conn.connectTimeout = 15000
                                            conn.readTimeout = 15000
                                            val html = conn.inputStream.bufferedReader().use { it.readText() }
                                            val doc = org.jsoup.Jsoup.parse(html, url)
                                            val pageTitle = doc.title().ifBlank { "WebPage" }
                                            val info = com.novelarrow.reader.data.browser.WebNovelDownloader.detectNovelInfo(url, pageTitle)
                                            val paragraphs = com.novelarrow.reader.data.parser.ChapterParser.parse(
                                                content = html,
                                                fileName = "${info.novelTitle}.html",
                                                novelUrl = url
                                            )
                                            val fullText = paragraphs.joinToString("\n\n") { it.spokenText }
                                            if (fullText.isNotBlank()) {
                                                AudioExportService.startExportText(
                                                    context = context,
                                                    title = info.novelTitle.ifBlank { pageTitle },
                                                    text = fullText,
                                                    speed = speed
                                                )
                                            } else {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Could not extract readable text from link", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            AppLogger.e("MainActivity", "Failed to audify link: ${e.message}", e)
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Failed to download link: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        val sharedText by sharedTextEvent
                        if (sharedText != null) {
                            IncomingTextShareDialog(
                                text = sharedText!!,
                                onDismiss = { sharedTextEvent.value = null },
                                onSaveAsMp3 = { text, speed ->
                                    AudioExportService.startExportText(
                                        context = context,
                                        title = "Shared_Note",
                                        text = text,
                                        speed = speed,
                                        audioFormat = com.novelarrow.reader.data.export.AudioExportEngine.AudioExportFormat.WAV
                                    )
                                }
                            )
                        }
                    }
                }
            }
            }
        }
    }

    private fun checkCssSelectorIntent(intent: Intent?) {
        if (intent?.action == NovelAudioPlaybackService.ACTION_EDIT_CSS_SELECTOR ||
            intent?.getBooleanExtra(NovelAudioPlaybackService.EXTRA_OPEN_CSS_SELECTOR, false) == true) {
            openCssSelectorEvent.value = true
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val hwSettings = com.novelarrow.reader.playback.AudioPlaybackCoordinator.settingsRepository?.getSettings()?.hardwareButtonSettings
        if (hwSettings != null && hwSettings.enabled) {
            if (com.novelarrow.reader.playback.HardwareButtonCoordinator.handleActivityKeyEvent(this, event, hwSettings)) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
private fun NotificationCssSelectorDialog(
    initialSelector: String,
    isEnabled: Boolean,
    onSave: (selector: String, enabled: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectorText by remember { mutableStateOf(initialSelector) }
    var enabled by remember { mutableStateOf(isEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text("HTML Content Selector", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Target specific main content container on web pages (e.g., article, #chapter-content, .entry-content). Leave empty for auto-detection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Content Selector", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                OutlinedTextField(
                    value = selectorText,
                    onValueChange = { selectorText = it },
                    label = { Text("CSS Selector") },
                    placeholder = { Text("e.g. #chapter-content, article") },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text("Common Presets:", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("#chapter-content", ".chapter-content", "article", ".entry-content").forEach { preset ->
                        FilterChip(
                            selected = selectorText == preset,
                            onClick = { selectorText = preset },
                            label = { Text(preset, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(selectorText.trim(), enabled)
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SignatureMismatchDialog(
    mismatchInfo: SignatureCompatibility.Mismatch?,
    downloadedApk: java.io.File?,
    onSaveToPathClick: () -> Unit,
    onForceInstallClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        shape = RoundedCornerShape(16.dp),
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                "One-Time Update Transition",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "This update uses BookReader's permanent official signing key for all future releases. Because older versions were built with temporary CI keys, Android requires a one-time reinstall.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "✅ No Data Loss",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Your reading progress, dark mode, pronunciation rules, skip words, and preferences are safely backed up in /NovelReader/.novelreader_preferences.json and will automatically restore when reinstalled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }

                Text(
                    "Steps to complete update:",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    "1. Tap 'Export / Save APK' to save update-latest.apk to your Downloads folder.\n" +
                    "2. Uninstall your current BookReader app.\n" +
                    "3. Open the saved APK in your file manager to install it.\n\n" +
                    "All future updates will then install smoothly with a single tap!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )

                if (downloadedApk != null) {
                    Text(
                        "Downloaded file: ${downloadedApk.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSaveToPathClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Export / Save APK")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onForceInstallClick) {
                    Text("Try Install Anyway", fontSize = 12.sp)
                }
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    )
}

