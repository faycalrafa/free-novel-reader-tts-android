package com.novelarrow.reader.ui.reader.delegate

import android.app.Application
import android.content.Context
import android.net.Uri
import com.novelarrow.reader.data.backup.BackupRestoreManager
import com.novelarrow.reader.data.backup.ExistingConfigsReport
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.model.ReaderSettings
import com.novelarrow.reader.data.model.WebBrowserSettings
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.data.repository.UserPreferencesBackup
import com.novelarrow.reader.ui.reader.ReaderUiState
import com.novelarrow.reader.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class ReaderConfigBackupDelegate(
    private val application: Application,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository,
    private val selectNovel: (Novel) -> Unit,
    private val scanRootFolder: (Uri) -> Unit,
    private val loadInitialLibrary: () -> Unit
) {
    fun checkForExistingConfigs(force: Boolean = false, onComplete: (Boolean) -> Unit = {}) {
        scope.launch {
            val prefs = application.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
            val alreadyHandled = prefs.getBoolean("has_handled_existing_configs", false)
            if (!force && alreadyHandled) {
                onComplete(false)
                return@launch
            }
            val report = BackupRestoreManager.detectExistingConfigs(application)
            if (report.hasConfigs) {
                uiState.update {
                    it.copy(
                        showExistingConfigsDialog = true,
                        existingConfigsReport = report
                    )
                }
                onComplete(true)
            } else {
                onComplete(false)
            }
        }
    }

    fun restoreExistingConfigs(report: ExistingConfigsReport) {
        scope.launch {
            val prefs = application.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("has_handled_existing_configs", true).apply()
            uiState.update { it.copy(showExistingConfigsDialog = false, isLoading = true) }
            BackupRestoreManager.restoreAllData(
                application,
                report,
                settingsRepository,
                libraryRepository
            )
            val novels = libraryRepository.getNovels()
            val target = novels.firstOrNull { it.id == report.libraryBackup?.lastActiveNovelId } ?: novels.firstOrNull()
            uiState.update {
                it.copy(
                    novels = novels,
                    isLoading = false,
                    toastMessage = "Restored preferences and reading progress."
                )
            }
            if (target != null) {
                selectNovel(target)
            }
        }
    }

    fun refuseAndDeleteExistingConfigs(report: ExistingConfigsReport) {
        scope.launch {
            val prefs = application.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("has_handled_existing_configs", true).apply()
            uiState.update { it.copy(showExistingConfigsDialog = false, isLoading = true) }
            BackupRestoreManager.deleteAllHiddenConfigs(application, report)
            // Reset to defaults
            val defaultSettings = ReaderSettings()
            val defaultBrowser = WebBrowserSettings()
            settingsRepository.applyRestoredBackup(
                UserPreferencesBackup(
                    readerSettings = defaultSettings,
                    browserSettings = defaultBrowser
                )
            )
            uiState.update {
                it.copy(
                    isLoading = false,
                    toastMessage = "Saved backup files deleted. Starting fresh."
                )
            }
            loadInitialLibrary()
        }
    }

    fun skipExistingConfigs() {
        val prefs = application.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("has_handled_existing_configs", true).apply()
        uiState.update { it.copy(showExistingConfigsDialog = false) }
        loadInitialLibrary()
    }

    fun checkAndImportDefaultDownloadFolderOnFirstLaunch() {
        val hasPermission = PermissionUtils.hasStoragePermission(application)
        if (!hasPermission) return // Don't consume first launch check if permission is not yet granted

        val prefs = application.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
        val alreadyChecked = prefs.getBoolean("has_checked_default_novelreader_folder", false)
        if (alreadyChecked) return

        val candidateDirs = BackupRestoreManager.getNovelReaderRoots(application)

        for (dir in candidateDirs) {
            if (dir.exists() && dir.isDirectory) {
                val hasChapters = hasAnySupportedChapters(dir)
                if (hasChapters) {
                    prefs.edit().putBoolean("has_checked_default_novelreader_folder", true).apply()
                    scanRootFolder(Uri.fromFile(dir))
                    return
                }
            }
        }
        prefs.edit().putBoolean("has_checked_default_novelreader_folder", true).apply()
    }

    private fun hasAnySupportedChapters(dir: File): Boolean {
        return try {
            val files = dir.listFiles() ?: return false
            files.any { file ->
                if (file.isDirectory) {
                    val subFiles = file.listFiles() ?: emptyArray()
                    subFiles.any { it.isFile && libraryRepository.isSupported(it.name) }
                } else {
                    file.isFile && libraryRepository.isSupported(file.name)
                }
            }
        } catch (_: Exception) {
            false
        }
    }
}
