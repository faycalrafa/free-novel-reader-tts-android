package com.novelarrow.reader.data.export

import android.content.Context
import android.os.Environment
import com.novelarrow.reader.util.AppLogger
import java.io.File
import java.util.Locale

data class AudioFileItem(
    val file: File,
    val name: String = file.nameWithoutExtension,
    val sizeBytes: Long = try { if (file.exists()) file.length() else 0L } catch (_: Exception) { 0L },
    val lastModified: Long = try { if (file.exists()) file.lastModified() else 0L } catch (_: Exception) { 0L }
) {
    val formattedSize: String
        get() = AudioLibraryScanner.formatFileSize(sizeBytes)
}

data class AudioFolderItem(
    val folder: File,
    val name: String = folder.name,
    val files: List<AudioFileItem>,
    val fileCount: Int = files.size,
    val totalSizeBytes: Long = files.sumOf { it.sizeBytes },
    val lastModified: Long = try { if (folder.exists()) folder.lastModified() else 0L } catch (_: Exception) { 0L }
) {
    val formattedSize: String
        get() = AudioLibraryScanner.formatFileSize(totalSizeBytes)
}

data class AudioLibraryContent(
    val folders: List<AudioFolderItem> = emptyList(),
    val standaloneFiles: List<AudioFileItem> = emptyList()
) {
    val isEmpty: Boolean get() = folders.isEmpty() && standaloneFiles.isEmpty()
    val totalFiles: Int get() = standaloneFiles.size + folders.sumOf { it.fileCount }
}

object AudioLibraryScanner {

    private const val TAG = "AudioLibraryScanner"

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val formatted = String.format(Locale.US, "%.1f", bytes / Math.pow(1024.0, digitGroups.toDouble()))
        return "$formatted ${units[digitGroups]}"
    }

    fun getScanDirectories(context: Context): List<File> {
        val dirs = mutableListOf<File>()
        try {
            val publicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "BookReader"
            )
            dirs.add(publicDir)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error resolving public Downloads directory: ${e.message}", e)
        }

        try {
            val appExternal = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
                File(it, "BookReader")
            }
            if (appExternal != null) {
                dirs.add(appExternal)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error resolving app-specific external files dir: ${e.message}", e)
        }

        return dirs
    }

    fun scan(context: Context): AudioLibraryContent {
        val dirs = getScanDirectories(context)
        return scanDirectories(dirs)
    }

    fun scanDirectories(directories: List<File>): AudioLibraryContent {
        val foldersMap = mutableMapOf<String, MutableList<AudioFileItem>>()
        val folderFilesMap = mutableMapOf<String, File>()
        val standaloneFiles = mutableListOf<AudioFileItem>()

        for (dir in directories) {
            if (!dir.exists() || !dir.isDirectory) continue

            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    val mp3s = child.listFiles { f ->
                        f.isFile && f.extension.equals("mp3", ignoreCase = true)
                    }?.map { AudioFileItem(it) }
                        ?.sortedBy { it.file.name.lowercase() }
                        ?: emptyList()

                    if (mp3s.isNotEmpty()) {
                        val key = child.name
                        folderFilesMap[key] = child
                        foldersMap.getOrPut(key) { mutableListOf() }.addAll(mp3s)
                    }
                } else if (child.isFile && child.extension.equals("mp3", ignoreCase = true)) {
                    standaloneFiles.add(AudioFileItem(child))
                }
            }
        }

        val folderItems = foldersMap.map { (name, files) ->
            val uniqueFiles = files.distinctBy { it.file.canonicalPath }
                .sortedBy { it.file.name.lowercase() }
            AudioFolderItem(
                folder = folderFilesMap[name] ?: File(name),
                name = name,
                files = uniqueFiles,
                fileCount = uniqueFiles.size,
                totalSizeBytes = uniqueFiles.sumOf { it.sizeBytes }
            )
        }.sortedByDescending { it.lastModified }

        val uniqueStandalone = standaloneFiles.distinctBy { it.file.canonicalPath }
            .sortedByDescending { it.lastModified }

        return AudioLibraryContent(
            folders = folderItems,
            standaloneFiles = uniqueStandalone
        )
    }
}
