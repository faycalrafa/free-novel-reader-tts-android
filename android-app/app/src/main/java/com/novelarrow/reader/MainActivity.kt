package com.novelarrow.reader

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelarrow.reader.data.export.FileFusionManager
import com.novelarrow.reader.data.repository.BrowserPlaybackSessionManager
import com.novelarrow.reader.playback.AudioExportService
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.HardwareButtonCoordinator
import com.novelarrow.reader.playback.NovelAudioPlaybackService
import com.novelarrow.reader.playback.QuickToolbarNotificationManager
import com.novelarrow.reader.theme.BookReaderTheme
import com.novelarrow.reader.ui.browser.BrowserViewModel
import com.novelarrow.reader.ui.onboarding.OnboardingSlideshow
import com.novelarrow.reader.ui.reader.ReaderViewModel
import com.novelarrow.reader.ui.reader.components.LibraryDrawerContent
import com.novelarrow.reader.util.AppLogger
import com.novelarrow.reader.util.PermissionUtils
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

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
            var hasVisitedBrowser by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(currentScreen) {
                if (currentScreen == AppScreen.BROWSER) {
                    hasVisitedBrowser = true
                }
            }
            var subScreenSourceScreen by rememberSaveable { mutableStateOf(AppScreen.SETTINGS) }
            val navigateToSubScreen: (AppScreen) -> Unit = { target ->
                if (currentScreen in listOf(AppScreen.READER, AppScreen.BROWSER, AppScreen.TEXT_TO_MP3, AppScreen.SETTINGS)) {
                    subScreenSourceScreen = currentScreen
                }
                currentScreen = target
            }
            val context = LocalContext.current
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            val sp = remember { context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE) }
            var isOnboardingCompleted by rememberSaveable {
                mutableStateOf(sp.getBoolean("has_completed_onboarding", false))
            }

            LaunchedEffect(Unit) {
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
                        AppLogger.e(TAG, "Failed to launch direct share chooser: ${e.message}", e)
                    }
                }
            }

            LaunchedEffect(isOnboardingCompleted) {
                if (isOnboardingCompleted) {
                    if (PermissionUtils.hasStoragePermission(context)) {
                        viewModel.checkAndImportDefaultDownloadFolderOnFirstLaunch()
                        val hasHandledConfigs = sp.getBoolean("has_handled_existing_configs", false)
                        if (!hasHandledConfigs) {
                            viewModel.checkForExistingConfigs()
                        }
                    }
                }
            }

            val shouldOpenCssSelector by openCssSelectorEvent
            var showCssSelectorDialog by remember { mutableStateOf(false) }
            var showGlobalAudioSettings by remember { mutableStateOf(false) }
            var pendingResumeBrowserSession by remember {
                mutableStateOf(
                    if (BrowserPlaybackSessionManager.shouldProposeResume(context)) {
                        BrowserPlaybackSessionManager.getLastSession(context)
                    } else null
                )
            }

            LaunchedEffect(shouldOpenCssSelector) {
                if (shouldOpenCssSelector) {
                    showCssSelectorDialog = true
                    openCssSelectorEvent.value = false
                }
            }

            BackHandler(enabled = drawerState.isOpen) {
                scope.launch { drawerState.close() }
            }

            BackHandler(enabled = !drawerState.isOpen && currentScreen in listOf(
                AppScreen.TOOLBAR_SETTINGS,
                AppScreen.HARDWARE_BUTTON_SETTINGS,
                AppScreen.SETTINGS_PRONUNCIATION,
                AppScreen.SETTINGS_SKIP_WORDS,
                AppScreen.SETTINGS_HEADER_FOOTER_SKIPS,
                AppScreen.SETTINGS_CONTENT_SELECTOR,
                AppScreen.VOICE_ENGINE,
                AppScreen.SETTINGS_BROWSER_IGNORED_SELECTORS,
                AppScreen.SETTINGS_BROWSER_NEXT_SELECTOR,
                AppScreen.BROWSER_REORDER_BUTTONS,
                AppScreen.BROWSER_HISTORY
            )) {
                currentScreen = subScreenSourceScreen
            }

            BackHandler(enabled = !drawerState.isOpen && currentScreen in listOf(AppScreen.DEV_LOGS, AppScreen.CHANGELOG)) {
                currentScreen = AppScreen.ABOUT
            }

            BackHandler(enabled = !drawerState.isOpen && currentScreen in listOf(AppScreen.ABOUT, AppScreen.SETTINGS, AppScreen.ARCHIVE, AppScreen.TEXT_TO_MP3)) {
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
                            sp.edit().putBoolean("has_completed_onboarding", true).apply()
                            isOnboardingCompleted = true
                            viewModel.checkAndImportDefaultDownloadFolderOnFirstLaunch()
                            val hasHandledConfigs = sp.getBoolean("has_handled_existing_configs", false)
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
                                    onSelectNovel = { novel -> viewModel.inspectNovel(novel) },
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
                                    onMoveChapter = { from, to -> viewModel.moveChapter(from, to) },
                                    onToggleChapterReadStatus = { chapter -> viewModel.toggleChapterReadStatus(chapter) },
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
                                    onCloseTab = { tabId -> browserViewModel.closeTab(tabId) },
                                    onOpenHistory = {
                                        navigateToSubScreen(AppScreen.BROWSER_HISTORY)
                                        scope.launch { drawerState.close() }
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
                                    onArchiveNovel = { novel -> viewModel.archiveNovel(novel) },
                                    onCloseDrawer = { scope.launch { drawerState.close() } }
                                )
                            }
                        }
                    ) {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            AppNavHost(
                                currentScreen = currentScreen,
                                onCurrentScreenChange = { currentScreen = it },
                                hasVisitedBrowser = hasVisitedBrowser,
                                viewModel = viewModel,
                                browserViewModel = browserViewModel,
                                uiState = uiState,
                                browserUiState = browserUiState,
                                navigateToSubScreen = navigateToSubScreen,
                                subScreenSourceScreen = subScreenSourceScreen,
                                folderPickerLauncher = folderPickerLauncher,
                                saveApkLauncher = saveApkLauncher,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onReplayOnboarding = {
                                    sp.edit().putBoolean("has_handled_existing_configs", false).apply()
                                    isOnboardingCompleted = false
                                    currentScreen = AppScreen.READER
                                },
                                onOpenAudioSettings = { showGlobalAudioSettings = true },
                                context = context
                            )

                            AppUpdateDialogs(
                                uiState = uiState,
                                viewModel = viewModel,
                                context = context,
                                saveApkLauncher = saveApkLauncher
                            )

                            GlobalAppDialogs(
                                uiState = uiState,
                                viewModel = viewModel,
                                browserViewModel = browserViewModel,
                                context = context,
                                prefs = sp,
                                showCssSelectorDialog = showCssSelectorDialog,
                                onDismissCssSelectorDialog = { showCssSelectorDialog = false },
                                showGlobalAudioSettings = showGlobalAudioSettings,
                                onDismissGlobalAudioSettings = { showGlobalAudioSettings = false },
                                navigateToSubScreen = navigateToSubScreen,
                                onOpenDevLogs = { currentScreen = AppScreen.DEV_LOGS },
                                resumeBrowserSession = pendingResumeBrowserSession,
                                onResumeBrowserSession = { session ->
                                    pendingResumeBrowserSession = null
                                    currentScreen = AppScreen.BROWSER
                                    browserViewModel.resumeSavedSession(session)
                                },
                                onDismissResumeBrowserSession = { session ->
                                    BrowserPlaybackSessionManager.dismissPrompt(context, session)
                                    pendingResumeBrowserSession = null
                                }
                            )

                            IncomingShareDialogs(
                                context = context,
                                scope = scope,
                                sharedUrl = sharedUrlEvent.value,
                                onClearSharedUrl = { sharedUrlEvent.value = null },
                                sharedText = sharedTextEvent.value,
                                onClearSharedText = { sharedTextEvent.value = null },
                                onOpenInBrowser = { url ->
                                    browserViewModel.openNewTab(url)
                                    currentScreen = AppScreen.BROWSER
                                },
                                onSaveFrozenHtml = { url ->
                                    browserViewModel.openNewTab(url)
                                    currentScreen = AppScreen.BROWSER
                                    Toast.makeText(context, "Opening page to save frozen HTML", Toast.LENGTH_SHORT).show()
                                }
                            )
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
        val hwSettings = AudioPlaybackCoordinator.settingsRepository?.getSettings()?.hardwareButtonSettings
        if (hwSettings != null && hwSettings.enabled) {
            if (HardwareButtonCoordinator.handleActivityKeyEvent(this, event, hwSettings)) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
