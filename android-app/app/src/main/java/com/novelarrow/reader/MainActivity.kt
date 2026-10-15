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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
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
import com.novelarrow.reader.ui.reader.LibraryDrawerContent
import com.novelarrow.reader.ui.reader.ReaderScreen
import com.novelarrow.reader.ui.reader.ReaderViewModel
import com.novelarrow.reader.ui.reader.UpdateAvailableDialog
import com.novelarrow.reader.ui.reader.UpdateDownloadingDialog
import kotlinx.coroutines.launch

enum class AppScreen {
    READER,
    ABOUT,
    BROWSER
}

class MainActivity : ComponentActivity() {
    private val openCssSelectorEvent = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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

            val shouldOpenCssSelector by openCssSelectorEvent
            var showCssSelectorDialog by remember { mutableStateOf(false) }

            LaunchedEffect(shouldOpenCssSelector) {
                if (shouldOpenCssSelector) {
                    showCssSelectorDialog = true
                    openCssSelectorEvent.value = false
                }
            }

            BackHandler(enabled = drawerState.isOpen) {
                scope.launch { drawerState.close() }
            }

            BackHandler(enabled = !drawerState.isOpen && currentScreen == AppScreen.ABOUT) {
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
                                    onNavigateBack = { currentScreen = AppScreen.READER }
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
