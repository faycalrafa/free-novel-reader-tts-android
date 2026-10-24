package com.novelarrow.reader

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.novelarrow.reader.data.updater.SignatureCompatibility
import com.novelarrow.reader.ui.reader.ReaderUiState
import com.novelarrow.reader.ui.reader.ReaderViewModel
import com.novelarrow.reader.ui.reader.components.UpdateAvailableDialog
import com.novelarrow.reader.ui.reader.components.UpdateDownloadingDialog
import java.io.File

@Composable
fun AppUpdateDialogs(
    uiState: ReaderUiState,
    viewModel: ReaderViewModel,
    context: Context,
    saveApkLauncher: ActivityResultLauncher<String>
) {
    if (uiState.showUpdateDialog && uiState.updateInfo != null) {
        val info = uiState.updateInfo
        UpdateAvailableDialog(
            updateInfo = info,
            downloadedApk = uiState.downloadedApk,
            onDownloadClick = { viewModel.startUpdateDownload() },
            onInstallClick = { viewModel.installDownloadedApk(context) },
            onSaveToPathClick = {
                val apkName = info.apkName.ifBlank { "update-latest.apk" }
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
}

@Composable
fun SignatureMismatchDialog(
    mismatchInfo: SignatureCompatibility.Mismatch?,
    downloadedApk: File?,
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
