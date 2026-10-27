package com.novelarrow.reader

import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import com.novelarrow.reader.ui.about.AboutScreen
import com.novelarrow.reader.ui.about.ChangelogScreen
import com.novelarrow.reader.ui.about.DevLogsScreen
import com.novelarrow.reader.ui.archive.ArchiveScreen
import com.novelarrow.reader.ui.browser.BrowserHistoryScreen
import com.novelarrow.reader.ui.browser.DownloadsHistoryScreen
import com.novelarrow.reader.ui.browser.BrowserReorderButtonsScreen
import com.novelarrow.reader.ui.browser.BrowserScreen
import com.novelarrow.reader.ui.browser.BrowserUiState
import com.novelarrow.reader.ui.browser.BrowserViewModel
import com.novelarrow.reader.ui.reader.ReaderScreen
import com.novelarrow.reader.ui.reader.ReaderUiState
import com.novelarrow.reader.ui.reader.ReaderViewModel
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

@Composable
fun AppNavHost(
    currentScreen: AppScreen,
    onCurrentScreenChange: (AppScreen) -> Unit,
    hasVisitedBrowser: Boolean,
    viewModel: ReaderViewModel,
    browserViewModel: BrowserViewModel,
    uiState: ReaderUiState,
    browserUiState: BrowserUiState,
    navigateToSubScreen: (AppScreen) -> Unit,
    subScreenSourceScreen: AppScreen,
    folderPickerLauncher: ActivityResultLauncher<Uri?>,
    saveApkLauncher: ActivityResultLauncher<String>,
    onOpenDrawer: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onOpenAudioSettings: () -> Unit,
    context: Context
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (hasVisitedBrowser) {
            val isBrowserActive = currentScreen == AppScreen.BROWSER
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (isBrowserActive) 1f else 0f)
                    .alpha(if (isBrowserActive) 1f else 0f)
            ) {
                BrowserScreen(
                    isActive = isBrowserActive,
                    viewModel = browserViewModel,
                    onNavigateBack = {
                        viewModel.refreshLibrary()
                        onCurrentScreenChange(AppScreen.READER)
                    },
                    onOpenDownloadedNovel = { novelId ->
                        viewModel.refreshLibrary(novelId)
                        onCurrentScreenChange(AppScreen.READER)
                    },
                    onOpenDrawer = onOpenDrawer,
                    onNavigateToPronunciationRules = { navigateToSubScreen(AppScreen.SETTINGS_PRONUNCIATION) },
                    onNavigateToSkipWords = { navigateToSubScreen(AppScreen.SETTINGS_SKIP_WORDS) },
                    onNavigateToVoiceEngine = { navigateToSubScreen(AppScreen.VOICE_ENGINE) },
                    onNavigateToHeaderFooterSkips = { navigateToSubScreen(AppScreen.SETTINGS_HEADER_FOOTER_SKIPS) },
                    onNavigateToContentSelector = { navigateToSubScreen(AppScreen.SETTINGS_CONTENT_SELECTOR) },
                    onNavigateToNextSelector = { navigateToSubScreen(AppScreen.SETTINGS_BROWSER_NEXT_SELECTOR) },
                    onNavigateToIgnoredSelectors = { navigateToSubScreen(AppScreen.SETTINGS_BROWSER_IGNORED_SELECTORS) },
                    onNavigateToReorderButtons = { navigateToSubScreen(AppScreen.BROWSER_REORDER_BUTTONS) },
                    onNavigateToHistory = { navigateToSubScreen(AppScreen.BROWSER_HISTORY) },
                    onNavigateToDownloads = { navigateToSubScreen(AppScreen.BROWSER_DOWNLOADS) },
                    onNavigateToDevLogs = { onCurrentScreenChange(AppScreen.DEV_LOGS) }
                )
            }
        }

        if (currentScreen != AppScreen.BROWSER) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f)
            ) {
                when (currentScreen) {
                    AppScreen.READER -> {
                        ReaderScreen(
                            viewModel = viewModel,
                            onNavigateToAbout = { onCurrentScreenChange(AppScreen.ABOUT) },
                            onNavigateToBrowser = { createNewTab ->
                                if (createNewTab) browserViewModel.openNewTab()
                                onCurrentScreenChange(AppScreen.BROWSER)
                            },
                            onOpenDrawer = onOpenDrawer,
                            onAddFolder = { folderPickerLauncher.launch(null) },
                            onNavigateToVoiceEngine = { navigateToSubScreen(AppScreen.VOICE_ENGINE) },
                            onNavigateToPronunciationRules = { navigateToSubScreen(AppScreen.SETTINGS_PRONUNCIATION) },
                            onNavigateToSkipWords = { navigateToSubScreen(AppScreen.SETTINGS_SKIP_WORDS) },
                            onNavigateToHeaderFooterSkips = { navigateToSubScreen(AppScreen.SETTINGS_HEADER_FOOTER_SKIPS) },
                            onNavigateToContentSelector = { navigateToSubScreen(AppScreen.SETTINGS_CONTENT_SELECTOR) },
                            onOpenAudioStudio = { onCurrentScreenChange(AppScreen.TEXT_TO_MP3) },
                            onResumeBrowserSession = { session ->
                                onCurrentScreenChange(AppScreen.BROWSER)
                                browserViewModel.resumeSavedSession(session)
                            }
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
                            onReplayOnboarding = onReplayOnboarding,
                            onNavigateToDevLogs = { onCurrentScreenChange(AppScreen.DEV_LOGS) },
                            onNavigateToChangelog = { onCurrentScreenChange(AppScreen.CHANGELOG) },
                            onNavigateBack = { onCurrentScreenChange(AppScreen.READER) },
                            onOpenDrawer = onOpenDrawer
                        )
                    }
                    AppScreen.DEV_LOGS -> {
                        DevLogsScreen(
                            installedVersionName = uiState.installedVersionName,
                            onNavigateBack = { onCurrentScreenChange(AppScreen.ABOUT) },
                            onOpenDrawer = onOpenDrawer
                        )
                    }
                    AppScreen.SETTINGS -> {
                        GlobalSettingsScreen(
                            currentTheme = uiState.settings.theme,
                            onThemeChange = { viewModel.updateTheme(it) },
                            onOpenDrawer = onOpenDrawer,
                            onNavigateToToolbarSettings = { navigateToSubScreen(AppScreen.TOOLBAR_SETTINGS) },
                            onNavigateToHardwareButtons = { navigateToSubScreen(AppScreen.HARDWARE_BUTTON_SETTINGS) },
                            onNavigateToVoiceEngine = { navigateToSubScreen(AppScreen.VOICE_ENGINE) },
                            onNavigateToPronunciationRules = { navigateToSubScreen(AppScreen.SETTINGS_PRONUNCIATION) },
                            onNavigateToSkipWords = { navigateToSubScreen(AppScreen.SETTINGS_SKIP_WORDS) },
                            onNavigateToHeaderFooterSkips = { navigateToSubScreen(AppScreen.SETTINGS_HEADER_FOOTER_SKIPS) },
                            onNavigateToContentSelector = { navigateToSubScreen(AppScreen.SETTINGS_CONTENT_SELECTOR) },
                            onNavigateToBrowserIgnoredSelectors = { navigateToSubScreen(AppScreen.SETTINGS_BROWSER_IGNORED_SELECTORS) },
                            onNavigateToBrowserNextSelector = { navigateToSubScreen(AppScreen.SETTINGS_BROWSER_NEXT_SELECTOR) },
                            onClearAllData = { viewModel.clearAllData() },
                            onOpenAudioSettings = onOpenAudioSettings,
                            onNavigateToAbout = { onCurrentScreenChange(AppScreen.ABOUT) }
                        )
                    }
                    AppScreen.VOICE_ENGINE -> {
                        VoiceEngineScreen(
                            onNavigateBack = { onCurrentScreenChange(subScreenSourceScreen) }
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
                            onNavigateBack = { onCurrentScreenChange(subScreenSourceScreen) }
                        )
                    }
                    AppScreen.SETTINGS_SKIP_WORDS -> {
                        SkipWordsScreen(
                            masterEnabled = uiState.settings.skipWordsEnabled,
                            skipWords = uiState.settings.skipWords,
                            ignoreSymbols = uiState.settings.skipWordsIgnoreSymbols,
                            onToggleMaster = { viewModel.updateSkipWordsEnabled(it) },
                            onToggleIgnoreSymbols = { viewModel.updateSkipWordsIgnoreSymbols(it) },
                            onAddSkipWord = { word -> viewModel.addSkipWord(word) },
                            onUpdateSkipWord = { word -> viewModel.updateSkipWord(word) },
                            onToggleSkipWord = { id, enabled -> viewModel.toggleSkipWord(id, enabled) },
                            onDeleteSkipWord = { id -> viewModel.deleteSkipWord(id) },
                            onNavigateBack = { onCurrentScreenChange(subScreenSourceScreen) }
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
                            onNavigateBack = { onCurrentScreenChange(subScreenSourceScreen) }
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
                            onNavigateBack = { onCurrentScreenChange(subScreenSourceScreen) }
                        )
                    }
                    AppScreen.SETTINGS_BROWSER_IGNORED_SELECTORS -> {
                        BrowserIgnoredSelectorsScreen(
                            ignoredSelectors = browserUiState.settings.ignoredSelectors,
                            onAddSelector = { browserViewModel.addIgnoredSelector(it) },
                            onRemoveSelector = { browserViewModel.removeIgnoredSelector(it) },
                            onResetDefaults = { browserViewModel.resetIgnoredSelectors() },
                            onNavigateBack = { onCurrentScreenChange(subScreenSourceScreen) }
                        )
                    }
                    AppScreen.SETTINGS_BROWSER_NEXT_SELECTOR -> {
                        BrowserNextSelectorScreen(
                            rules = browserUiState.settings.nextChapterRules,
                            onAddRule = { browserViewModel.addNextChapterSelectorRule(it) },
                            onUpdateRule = { browserViewModel.updateNextChapterSelectorRule(it) },
                            onToggleRule = { id, enabled -> browserViewModel.toggleNextChapterSelectorRule(id, enabled) },
                            onDeleteRule = { id -> browserViewModel.deleteNextChapterSelectorRule(id) },
                            onResetDefaults = { browserViewModel.resetNextChapterSelectorRules() },
                            onNavigateBack = { onCurrentScreenChange(subScreenSourceScreen) }
                        )
                    }
                    AppScreen.TOOLBAR_SETTINGS -> {
                        ToolbarSettingsScreen(
                            toolbarSettings = uiState.settings.quickToolbarSettings,
                            onUpdateSettings = { viewModel.updateQuickToolbarSettings(it) },
                            onNavigateBack = { onCurrentScreenChange(subScreenSourceScreen) },
                            onOpenDrawer = onOpenDrawer
                        )
                    }
                    AppScreen.HARDWARE_BUTTON_SETTINGS -> {
                        HardwareButtonsScreen(
                            settings = uiState.settings.hardwareButtonSettings,
                            onUpdateSettings = { viewModel.updateHardwareButtonSettings(it) },
                            onNavigateBack = { onCurrentScreenChange(subScreenSourceScreen) },
                            onOpenDrawer = onOpenDrawer
                        )
                    }
                    AppScreen.BROWSER -> {
                        // Handled in retained Box above
                    }
                    AppScreen.BROWSER_REORDER_BUTTONS -> {
                        BrowserReorderButtonsScreen(
                            currentOrder = browserUiState.settings.toolbarButtonOrder,
                            customButtons = browserUiState.customToolbarButtons,
                            shortcuts = browserUiState.settings.navbarShortcuts,
                            onOrderChanged = { newOrder ->
                                browserViewModel.updateToolbarButtonOrder(newOrder)
                            },
                            onNavigateBack = { onCurrentScreenChange(subScreenSourceScreen) }
                        )
                    }
                    AppScreen.BROWSER_HISTORY -> {
                        BrowserHistoryScreen(
                            history = browserUiState.historyEntries,
                            onSelectUrl = { url ->
                                browserViewModel.navigateTo(url)
                                onCurrentScreenChange(AppScreen.BROWSER)
                            },
                            onOpenInNewTab = { url ->
                                browserViewModel.openNewTab(url)
                                onCurrentScreenChange(AppScreen.BROWSER)
                            },
                            onDeleteEntry = { id -> browserViewModel.deleteHistoryEntry(id) },
                            onClearHistory = { browserViewModel.clearHistory() },
                            onNavigateBack = { onCurrentScreenChange(subScreenSourceScreen) }
                        )
                    }
                    AppScreen.BROWSER_DOWNLOADS -> {
                        val downloadEntries by browserViewModel.downloadHistoryFlow.collectAsState()
                        DownloadsHistoryScreen(
                            downloads = downloadEntries,
                            onDeleteEntry = { id -> browserViewModel.deleteDownloadEntry(id) },
                            onClearHistory = { browserViewModel.clearDownloadHistory() },
                            onNavigateBack = { onCurrentScreenChange(subScreenSourceScreen) }
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
                            onNavigateBack = { onCurrentScreenChange(AppScreen.READER) },
                            onOpenDrawer = onOpenDrawer
                        )
                    }
                    AppScreen.CHANGELOG -> {
                        ChangelogScreen(
                            onNavigateBack = { onCurrentScreenChange(AppScreen.ABOUT) }
                        )
                    }
                    AppScreen.TEXT_TO_MP3 -> {
                        AudioStudioScreen(
                            novels = uiState.novels,
                            currentNovel = uiState.currentNovel,
                            onNavigateBack = { onCurrentScreenChange(AppScreen.READER) },
                            onOpenDrawer = onOpenDrawer,
                            onNavigateToVoiceEngine = { navigateToSubScreen(AppScreen.VOICE_ENGINE) },
                            onNavigateToPronunciationRules = { navigateToSubScreen(AppScreen.SETTINGS_PRONUNCIATION) },
                            onNavigateToSkipWords = { navigateToSubScreen(AppScreen.SETTINGS_SKIP_WORDS) },
                            onNavigateToHeaderFooterSkips = { navigateToSubScreen(AppScreen.SETTINGS_HEADER_FOOTER_SKIPS) },
                            onNavigateToContentSelector = { navigateToSubScreen(AppScreen.SETTINGS_CONTENT_SELECTOR) }
                        )
                    }
                }
            }
        }
    }
}
