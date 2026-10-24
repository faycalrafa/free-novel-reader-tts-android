package com.novelarrow.reader.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.novelarrow.reader.data.browser.WebNovelDownloader
import com.novelarrow.reader.util.AppLogger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

internal object FolderProgressSync {

    private const val TAG = "FolderProgressSync"

    fun readFolderProgress(
        context: Context,
        folderUriOrPath: String?,
        novelTitle: String,
        folderDocId: String? = null,
        json: Json
    ): NovelFolderProgress? {
        try {
            AppLogger.d(TAG, "Attempting to read folder progress for '$novelTitle' (path: $folderUriOrPath, docId: $folderDocId)")
            if (!folderUriOrPath.isNullOrBlank()) {
                if (folderUriOrPath.startsWith("/") || folderUriOrPath.startsWith("file://")) {
                    val dir = File(folderUriOrPath.removePrefix("file://"))
                    val progressFile = File(dir, ".progress.json")
                    if (progressFile.exists() && progressFile.isFile) {
                        val text = progressFile.readText(Charsets.UTF_8)
                        if (text.isNotBlank()) {
                            val parsed = json.decodeFromString<NovelFolderProgress>(text)
                            AppLogger.i(TAG, "Loaded .progress.json for '$novelTitle' from filesystem: ${parsed.chapters.size} chapters tracked (currentIdx=${parsed.currentChapterIndex})")
                            return parsed
                        }
                    }
                } else if (folderUriOrPath.startsWith("content://")) {
                    val uri = Uri.parse(folderUriOrPath)
                    if (folderDocId != null) {
                        try {
                            val (_, files) = SafTreeScanner.querySafChildrenFast(context, uri, folderDocId)
                            val progressItem = files.firstOrNull { it.displayName == ".progress.json" }
                            if (progressItem != null) {
                                context.contentResolver.openInputStream(Uri.parse(progressItem.uriStr))?.use { stream ->
                                    val text = stream.bufferedReader(Charsets.UTF_8).readText()
                                    if (text.isNotBlank()) {
                                        val parsed = json.decodeFromString<NovelFolderProgress>(text)
                                        AppLogger.i(TAG, "Loaded SAF .progress.json for '$novelTitle' (docId $folderDocId): ${parsed.chapters.size} chapters tracked")
                                        return parsed
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            AppLogger.w(TAG, "Error reading SAF subfolder progress for $novelTitle (docId: $folderDocId)", e)
                        }
                    } else {
                        val rootDoc = try { DocumentFile.fromTreeUri(context, uri) } catch (e: Exception) {
                            AppLogger.w(TAG, "Failed resolving DocumentFile from $uri for $novelTitle", e)
                            null
                        }
                        val progressFile = rootDoc?.findFile(".progress.json")
                        if (progressFile != null && progressFile.exists()) {
                            context.contentResolver.openInputStream(progressFile.uri)?.use { stream ->
                                val text = stream.bufferedReader(Charsets.UTF_8).readText()
                                if (text.isNotBlank()) {
                                    val parsed = json.decodeFromString<NovelFolderProgress>(text)
                                    AppLogger.i(TAG, "Loaded rootDoc .progress.json for '$novelTitle': ${parsed.chapters.size} chapters tracked")
                                    return parsed
                                }
                            }
                        }
                    }
                }
            }

            // Fallback: check standard NovelReader directory for this novel title
            val safeTitle = WebNovelDownloader.sanitizeFileName(novelTitle)
            val candidateDirs = mutableListOf<File>()
            candidateDirs.add(File(context.filesDir, "NovelReader/$safeTitle"))
            candidateDirs.add(File(context.filesDir, "NovelReader/$novelTitle"))
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
                candidateDirs.add(File(it, "NovelReader/$safeTitle"))
                candidateDirs.add(File(it, "NovelReader/$novelTitle"))
            }
            for (root in com.novelarrow.reader.data.backup.BackupRestoreManager.getNovelReaderRoots(context)) {
                candidateDirs.add(File(root, safeTitle))
                candidateDirs.add(File(root, novelTitle))
            }
            for (candidate in candidateDirs.distinctBy { it.absolutePath }) {
                val pFile = File(candidate, ".progress.json")
                if (pFile.exists() && pFile.isFile && pFile.length() > 0L) {
                    val text = pFile.readText(Charsets.UTF_8)
                    if (text.isNotBlank()) {
                        val parsed = json.decodeFromString<NovelFolderProgress>(text)
                        AppLogger.i(TAG, "Loaded fallback .progress.json for '$novelTitle' from ${candidate.absolutePath}: ${parsed.chapters.size} chapters tracked")
                        return parsed
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed reading folder progress for $novelTitle: ${e.message}", e)
        }
        return null
    }

    fun writeFolderProgress(
        context: Context,
        folderUriOrPath: String?,
        novelTitle: String,
        progress: NovelFolderProgress,
        folderDocId: String? = null,
        json: Json
    ) {
        try {
            AppLogger.d(TAG, "Saving .progress.json for '$novelTitle' (curIdx=${progress.currentChapterIndex}, chapters=${progress.chapters.size})")
            val jsonText = json.encodeToString(progress)
            if (!folderUriOrPath.isNullOrBlank()) {
                if (folderUriOrPath.startsWith("/") || folderUriOrPath.startsWith("file://")) {
                    val dir = File(folderUriOrPath.removePrefix("file://"))
                    if (dir.exists() && dir.isDirectory) {
                        File(dir, ".progress.json").writeText(jsonText, Charsets.UTF_8)
                        AppLogger.i(TAG, "Successfully saved .progress.json to filesystem: ${dir.absolutePath}")
                    }
                } else if (folderUriOrPath.startsWith("content://")) {
                    val uri = Uri.parse(folderUriOrPath)
                    if (folderDocId != null) {
                        try {
                            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(uri, folderDocId)
                            val (_, files) = SafTreeScanner.querySafChildrenFast(context, uri, folderDocId)
                            val existingProgress = files.firstOrNull { it.displayName == ".progress.json" }
                            val targetUri = if (existingProgress != null) {
                                Uri.parse(existingProgress.uriStr)
                            } else {
                                DocumentsContract.createDocument(context.contentResolver, parentDocUri, "application/json", ".progress.json")
                            }
                            targetUri?.let { pUri ->
                                context.contentResolver.openOutputStream(pUri, "wt")?.use { out ->
                                    out.write(jsonText.toByteArray(Charsets.UTF_8))
                                }
                                AppLogger.i(TAG, "Successfully saved SAF .progress.json for docId $folderDocId")
                            }
                        } catch (e: Exception) {
                            AppLogger.w(TAG, "Error writing SAF subfolder progress for $novelTitle (docId: $folderDocId)", e)
                        }
                    } else {
                        val rootDoc = try { DocumentFile.fromTreeUri(context, uri) } catch (e: Exception) {
                            AppLogger.w(TAG, "Failed resolving tree uri for $folderUriOrPath", e)
                            null
                        }
                        if (rootDoc != null && rootDoc.exists() && rootDoc.canWrite()) {
                            var progressFile = rootDoc.findFile(".progress.json")
                            if (progressFile == null) {
                                progressFile = rootDoc.createFile("application/json", ".progress.json")
                            }
                            progressFile?.uri?.let { pUri ->
                                context.contentResolver.openOutputStream(pUri, "wt")?.use { out ->
                                    out.write(jsonText.toByteArray(Charsets.UTF_8))
                                }
                                AppLogger.i(TAG, "Successfully saved SAF root .progress.json for $novelTitle")
                            }
                        }
                    }
                }
            }

            val safeTitle = WebNovelDownloader.sanitizeFileName(novelTitle)
            val candidateDirs = mutableListOf<File>()
            candidateDirs.add(File(context.filesDir, "NovelReader/$safeTitle"))
            candidateDirs.add(File(context.filesDir, "NovelReader/$novelTitle"))
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
                candidateDirs.add(File(it, "NovelReader/$safeTitle"))
                candidateDirs.add(File(it, "NovelReader/$novelTitle"))
            }
            for (root in com.novelarrow.reader.data.backup.BackupRestoreManager.getNovelReaderRoots(context)) {
                candidateDirs.add(File(root, safeTitle))
                candidateDirs.add(File(root, novelTitle))
            }
            for (candidate in candidateDirs.distinctBy { it.absolutePath }) {
                if (candidate.exists() && candidate.isDirectory) {
                    try {
                        File(candidate, ".progress.json").writeText(jsonText, Charsets.UTF_8)
                        AppLogger.d(TAG, "Synced fallback .progress.json to ${candidate.absolutePath}")
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Failed writing fallback progress in ${candidate.absolutePath}: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to write folder progress for $novelTitle: ${e.message}", e)
        }
    }
}
