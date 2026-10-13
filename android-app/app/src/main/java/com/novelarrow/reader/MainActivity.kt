package com.novelarrow.reader

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelarrow.reader.theme.NovelArrowReaderTheme
import com.novelarrow.reader.ui.about.AboutScreen
import com.novelarrow.reader.ui.reader.ReaderScreen
import com.novelarrow.reader.ui.reader.ReaderViewModel
import com.novelarrow.reader.ui.reader.UpdateAvailableDialog
import com.novelarrow.reader.ui.reader.UpdateDownloadingDialog

enum class AppScreen {
    READER,
    ABOUT
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
            val uiState by viewModel.uiState.collectAsState()
            var currentScreen by remember { mutableStateOf(AppScreen.READER) }
            val context = LocalContext.current

            LaunchedEffect(uiState.toastMessage) {
                uiState.toastMessage?.let { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    viewModel.clearToast()
                }
            }

            NovelArrowReaderTheme(themeMode = uiState.settings.theme) {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (currentScreen) {
                        AppScreen.READER -> {
                            ReaderScreen(
                                viewModel = viewModel,
                                onNavigateToAbout = { currentScreen = AppScreen.ABOUT }
                            )
                        }
                        AppScreen.ABOUT -> {
                            AboutScreen(
                                installedVersionName = uiState.installedVersionName,
                                isCheckingUpdate = uiState.isCheckingUpdate,
                                downloadedApk = uiState.downloadedApk,
                                onCheckForUpdates = { viewModel.checkForUpdate(silent = false) },
                                onInstallDownloadedApk = { viewModel.installDownloadedApk(context) },
                                onNavigateBack = { currentScreen = AppScreen.READER }
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
