package com.novelarrow.reader.ui.reader.delegate

import android.app.Application
import android.content.Context
import android.net.Uri
import com.novelarrow.reader.data.updater.AppUpdateManager
import com.novelarrow.reader.data.updater.CheckUpdateResult
import com.novelarrow.reader.data.updater.SignatureCompatibility
import com.novelarrow.reader.ui.reader.ReaderUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReaderAppUpdateDelegate(
    private val application: Application,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<ReaderUiState>
) {
    private var downloadJob: Job? = null

    fun checkForUpdate(silent: Boolean = false) {
        scope.launch {
            if (uiState.value.isCheckingUpdate) return@launch
            uiState.update { it.copy(isCheckingUpdate = true) }
            val currentVer = uiState.value.installedVersionName
            when (val result = AppUpdateManager.checkForUpdate(currentVer)) {
                is CheckUpdateResult.UpdateAvailable -> {
                    uiState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            updateInfo = result.updateInfo,
                            showUpdateDialog = true
                        )
                    }
                }
                is CheckUpdateResult.UpToDate -> {
                    uiState.update { it.copy(isCheckingUpdate = false) }
                    if (!silent) {
                        uiState.update {
                            it.copy(toastMessage = "You're on the latest version (${result.currentVersion}).")
                        }
                    }
                }
                is CheckUpdateResult.Error -> {
                    uiState.update { it.copy(isCheckingUpdate = false) }
                    if (!silent) {
                        uiState.update {
                            it.copy(toastMessage = "Update check failed: ${result.message}")
                        }
                    }
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        uiState.update { it.copy(showUpdateDialog = false) }
    }

    fun startUpdateDownload() {
        val info = uiState.value.updateInfo ?: return
        val context = application
        downloadJob?.cancel()
        downloadJob = scope.launch {
            uiState.update {
                it.copy(
                    isDownloadingUpdate = true,
                    updateProgress = 0f,
                    updateDownloadedBytes = 0L,
                    updateTotalBytes = info.apkSize
                )
            }
            try {
                val apkFile = AppUpdateManager.downloadApk(
                    downloadUrl = info.downloadUrl,
                    expectedSize = info.apkSize,
                    context = context,
                    onProgress = { bytesRead, totalBytes ->
                        val progress = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                        uiState.update {
                            it.copy(
                                updateProgress = progress,
                                updateDownloadedBytes = bytesRead,
                                updateTotalBytes = totalBytes
                            )
                        }
                    }
                )
                uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        downloadedApk = apkFile
                    )
                }
                // Automatically prompt installation
                installDownloadedApk(context)
            } catch (e: CancellationException) {
                uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        toastMessage = "Update download was cancelled."
                    )
                }
            } catch (e: Exception) {
                uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        toastMessage = "Download failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun cancelUpdateDownload() {
        downloadJob?.cancel()
        downloadJob = null
        uiState.update { it.copy(isDownloadingUpdate = false) }
    }

    fun installDownloadedApk(context: Context, force: Boolean = false) {
        val apkFile = uiState.value.downloadedApk ?: return
        try {
            if (!force) {
                val compatibility = AppUpdateManager.checkSignatureCompatibility(context, apkFile)
                if (compatibility is SignatureCompatibility.Mismatch) {
                    uiState.update {
                        it.copy(
                            showSignatureMismatchDialog = true,
                            signatureMismatchInfo = compatibility
                        )
                    }
                    return
                }
            }

            if (!AppUpdateManager.canRequestPackageInstalls(context)) {
                uiState.update {
                    it.copy(toastMessage = "Please allow installing unknown apps from this source.")
                }
                AppUpdateManager.openInstallPermissionSettings(context)
            } else {
                AppUpdateManager.installApk(context, apkFile)
            }
        } catch (e: Exception) {
            uiState.update {
                it.copy(toastMessage = "Failed to launch installer: ${e.message}")
            }
        }
    }

    fun dismissSignatureMismatchDialog() {
        uiState.update {
            it.copy(
                showSignatureMismatchDialog = false,
                signatureMismatchInfo = null
            )
        }
    }

    fun saveApkToCustomUri(destinationUri: Uri, context: Context) {
        scope.launch {
            val apkFile = uiState.value.downloadedApk
            if (apkFile != null && apkFile.exists()) {
                val success = AppUpdateManager.copyApkToUri(context, apkFile, destinationUri)
                uiState.update {
                    it.copy(
                        toastMessage = if (success) "Saved update-latest.apk to selected path" else "Failed to save APK to selected path"
                    )
                }
            } else {
                val info = uiState.value.updateInfo
                val downloadUrl = info?.downloadUrl ?: "https://github.com/faycalrafa/free-novel-reader-tts-android/releases/latest/download/update-latest.apk"
                val apkSize = info?.apkSize ?: 0L
                uiState.update {
                    it.copy(
                        isDownloadingUpdate = true,
                        updateProgress = 0f,
                        updateDownloadedBytes = 0L,
                        updateTotalBytes = apkSize
                    )
                }
                try {
                    val tempApk = AppUpdateManager.downloadApk(
                        downloadUrl = downloadUrl,
                        expectedSize = apkSize,
                        context = context,
                        onProgress = { bytesRead, totalBytes ->
                            val progress = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                            uiState.update {
                                it.copy(
                                    updateProgress = progress,
                                    updateDownloadedBytes = bytesRead,
                                    updateTotalBytes = totalBytes
                                )
                            }
                        }
                    )
                    uiState.update {
                        it.copy(
                            isDownloadingUpdate = false,
                            downloadedApk = tempApk
                        )
                    }
                    val success = AppUpdateManager.copyApkToUri(context, tempApk, destinationUri)
                    uiState.update {
                        it.copy(
                            toastMessage = if (success) "Saved update-latest.apk to selected path" else "Failed to save APK to selected path"
                        )
                    }
                } catch (e: Exception) {
                    uiState.update {
                        it.copy(
                            isDownloadingUpdate = false,
                            toastMessage = "Download failed: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    fun onCleared() {
        downloadJob?.cancel()
    }
}
