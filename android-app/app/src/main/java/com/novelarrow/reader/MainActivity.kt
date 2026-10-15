package com.novelarrow.reader

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import android.content.Context
import com.novelarrow.reader.playback.QuickToolbarNotificationManager
import androidx.compose.material.icons.filled.Folder
import com.novelarrow.reader.ui.onboarding.OnboardingSlideshow
import com.novelarrow.reader.util.PermissionUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextAlign
import com.novelarrow.reader.data.updater.SignatureCompatibility
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelarrow.reader.playback.NovelAudioPlaybackService
import com.novelarrow.reader.theme.NovelArrowReaderTheme
import com.novelarrow.reader.ui.about.AboutScreen
import com.novelarrow.reader.ui.browser.BrowserScreen
import com.novelarrow.reader.ui.browser.BrowserViewModel
import com.novelarrow.reader.ui.common.UnifiedAudioSettingsDialog
import com.novelarrow.reader.ui.reader.LibraryDrawerContent
import com.novelarrow.reader.ui.reader.ReaderScreen
import com.novelarrow.reader.ui.reader.ReaderViewModel
import com.novelarrow.reader.ui.reader.UpdateAvailableDialog
import com.novelarrow.reader.ui.reader.UpdateDownloadingDialog
import com.novelarrow.reader.ui.settings.GlobalSettingsScreen
import com.novelarrow.reader.ui.settings.ToolbarSettingsScreen
import kotlinx.coroutines.launch

enum class AppScreen {
    READER,
    ABOUT,
    BROWSER,
    SETTINGS,
    TOOLBAR_SETTINGS
}

class MainActivity : ComponentActivity() {
    private val openCssSelectorEvent = mutableStateOf(false)

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

        setContent {
            val viewModel: ReaderViewModel = viewModel()
            val browserViewModel: BrowserViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()
            val browserUiState by browserViewModel.uiState.collectAsState()
            var currentScreen by rememberSaveable { mutableStateOf(AppScreen.READER) }
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

            BackHandler(enabled = !drawerState.isOpen && (currentScreen == AppScreen.ABOUT || currentScreen == AppScreen.SETTINGS)) {
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
                NovelArrowReaderTheme(themeMode = uiState.settings.theme) {
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
                NovelArrowReaderTheme(themeMode = uiState.settings.theme) {
                    ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = currentScreen != AppScreen.BROWSER,
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.width(320.dp)
                        ) {
                            LibraryDrawerContent(
                                novels = uiState.novels,
                                currentNovel = uiState.currentNovel,
                                chapters = uiState.chapters,
                                currentChapter = uiState.currentChapter,
                                browserTabs = browserUiState.tabs,
                                currentTabId = browserUiState.currentTabId,
                                installedVersionName = uiState.installedVersionName,
                                onSelectNovel = { novel ->
                                    viewModel.selectNovel(novel)
                                },
                                onSelectChapter = { index ->
                                    viewModel.selectChapter(index)
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
                                    onAddFolder = { folderPickerLauncher.launch(null) }
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
                                    onNavigateBack = { currentScreen = AppScreen.READER }
                                )
                            }
                            AppScreen.SETTINGS -> {
                                GlobalSettingsScreen(
                                    onNavigateBack = { currentScreen = AppScreen.READER },
                                    onNavigateToToolbarSettings = { currentScreen = AppScreen.TOOLBAR_SETTINGS },
                                    onOpenAudioSettings = { showGlobalAudioSettings = true },
                                    onOpenCssSelector = { showCssSelectorDialog = true },
                                    onNavigateToAbout = { currentScreen = AppScreen.ABOUT }
                                )
                            }
                            AppScreen.TOOLBAR_SETTINGS -> {
                                ToolbarSettingsScreen(
                                    toolbarSettings = uiState.settings.quickToolbarSettings,
                                    onUpdateSettings = { viewModel.updateQuickToolbarSettings(it) },
                                    onNavigateBack = { currentScreen = AppScreen.SETTINGS }
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
                    }
                }
            }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkCssSelectorIntent(intent)
    }

    private fun checkCssSelectorIntent(intent: Intent?) {
        if (intent?.action == NovelAudioPlaybackService.ACTION_EDIT_CSS_SELECTOR ||
            intent?.getBooleanExtra(NovelAudioPlaybackService.EXTRA_OPEN_CSS_SELECTOR, false) == true) {
            openCssSelectorEvent.value = true
        }
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
                    "This update uses NovelArrow's permanent official signing key for all future releases. Because older versions were built with temporary CI keys, Android requires a one-time reinstall.",
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
                    "2. Uninstall your current NovelArrow Reader app.\n" +
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

