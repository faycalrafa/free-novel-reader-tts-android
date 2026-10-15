package com.novelarrow.reader

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            val viewModel: ReaderViewModel = viewModel()
            val browserViewModel: BrowserViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()
            val browserUiState by browserViewModel.uiState.collectAsState()
            var currentScreen by rememberSaveable { mutableStateOf(AppScreen.READER) }
            val context = LocalContext.current
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

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
                    }
                }
            }
        }
    }
}
