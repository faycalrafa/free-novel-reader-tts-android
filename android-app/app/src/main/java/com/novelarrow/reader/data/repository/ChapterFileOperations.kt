package com.novelarrow.reader.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.novelarrow.reader.data.browser.WebNovelDownloader
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.util.AppLogger
import java.io.File

internal object ChapterFileOperations {

    private const val TAG = "ChapterFileOperations"

    fun readChapterContent(context: Context, chapter: Chapter): String {
        val fileUriStr = chapter.fileUri
        val localFile = File(fileUriStr)
        if (localFile.exists() && localFile.isFile) {
            return localFile.readText(Charsets.UTF_8)
        } else {
            val uri = Uri.parse(fileUriStr)
            if (uri.scheme == "file") {
                return File(uri.path ?: "").readText(Charsets.UTF_8)
            } else {
                try {
                    return context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: throw IllegalStateException("Could not read chapter file at ${chapter.fileUri}")
                } catch (e: SecurityException) {
                    AppLogger.e(TAG, "Storage permission revoked for chapter ${chapter.title} (${chapter.fileUri}): ${e.message}", e)
                    throw IllegalStateException("Storage permission was revoked by Android for this chapter file. Please re-import the novel folder into the app.")
                }
            }
        }
    }

    fun saveChapterContent(context: Context, chapter: Chapter, newContent: String): Boolean {
        try {
            val fileUriStr = chapter.fileUri
            val localFile = File(fileUriStr)
            if (localFile.exists() && localFile.isFile) {
                localFile.writeText(newContent, Charsets.UTF_8)
                return true
            }
            val uri = Uri.parse(fileUriStr)
            if (uri.scheme == "file") {
                File(uri.path ?: "").writeText(newContent, Charsets.UTF_8)
                return true
            }
            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(newContent)
                }
                return true
            }
            return false
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed saving content to chapter ${chapter.fileUri}", e)
            return false
        }
    }

    fun deleteSingleChapterFile(context: Context, chapter: Chapter) {
        try {
            if (chapter.fileUri.startsWith("/") || chapter.fileUri.startsWith("file://")) {
                val f = File(chapter.fileUri.removePrefix("file://"))
                if (f.exists()) f.delete()
            } else if (chapter.fileUri.startsWith("content://")) {
                val uri = Uri.parse(chapter.fileUri)
                var deleted = false
                try {
                    deleted = DocumentsContract.deleteDocument(context.contentResolver, uri)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Direct DocumentsContract.deleteDocument failed for ${chapter.fileUri}: ${e.message}", e)
                }
                if (!deleted) {
                    try {
                        val doc = DocumentFile.fromSingleUri(context, uri)
                        if (doc != null && doc.exists()) {
                            doc.delete()
                        }
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "DocumentFile.delete fallback failed for ${chapter.fileUri}: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed deleting single chapter file ${chapter.fileUri}: ${e.message}", e)
        }
    }

    fun deleteNovelFilesFromDisk(context: Context, novel: Novel, chapters: List<Chapter>?) {
        try {
            AppLogger.i(TAG, "Deleting files from disk for novel: ${novel.title} (id=${novel.id}, loose=${novel.isLooseRootFiles}, subfolder=${novel.subfolderPath}, docId=${novel.folderDocId})")

            // 1. Delete folder progress file (.progress.json) if it exists
            try {
                if (novel.subfolderPath != null) {
                    val pFile = File(novel.subfolderPath, ".progress.json")
                    if (pFile.exists()) pFile.delete()
                } else if (novel.rootTreeUri.startsWith("/") || novel.rootTreeUri.startsWith("file://")) {
                    val pFile = File(novel.rootTreeUri.removePrefix("file://"), ".progress.json")
                    if (pFile.exists()) pFile.delete()
                } else if (novel.rootTreeUri.startsWith("content://")) {
                    val treeUri = Uri.parse(novel.rootTreeUri)
                    if (novel.folderDocId != null) {
                        val (_, files) = SafTreeScanner.querySafChildrenFast(context, treeUri, novel.folderDocId)
                        files.firstOrNull { it.displayName == ".progress.json" }?.let { pFile ->
                            DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(pFile.uriStr))
                        }
                    } else {
                        val rootDoc = try { DocumentFile.fromTreeUri(context, treeUri) } catch (e: Exception) { null }
                        rootDoc?.findFile(".progress.json")?.delete()
                    }
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed deleting .progress.json for ${novel.title}: ${e.message}", e)
            }

            // 2. If novel is loose root files, preserve parent folder
            if (novel.isLooseRootFiles) {
                AppLogger.i(TAG, "Novel is loose root files: preserving parent folder ${novel.rootTreeUri}")
            } else if (novel.subfolderPath != null) {
                val subDir = File(novel.subfolderPath)
                if (subDir.exists() && subDir.isDirectory) {
                    AppLogger.i(TAG, "Deleting local subfolder: ${subDir.absolutePath}")
                    subDir.deleteRecursively()
                }
            } else if (novel.folderDocId != null && novel.rootTreeUri.startsWith("content://")) {
                try {
                    val treeUri = Uri.parse(novel.rootTreeUri)
                    val subfolderUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, novel.folderDocId)
                    AppLogger.i(TAG, "Deleting SAF subfolder document: ${novel.folderDocId}")
                    val deleted = DocumentsContract.deleteDocument(context.contentResolver, subfolderUri)
                    if (!deleted) {
                        AppLogger.w(TAG, "SAF deleteDocument returned false for ${novel.folderDocId}")
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed deleting SAF subfolder document for ${novel.title}", e)
                }
            } else {
                if (novel.rootTreeUri.startsWith("/") || novel.rootTreeUri.startsWith("file://")) {
                    val path = novel.rootTreeUri.removePrefix("file://")
                    val dir = File(path)
                    if (dir.exists()) {
                        dir.deleteRecursively()
                    }
                } else if (novel.rootTreeUri.startsWith("content://")) {
                    try {
                        val treeUri = Uri.parse(novel.rootTreeUri)
                        val docFile = DocumentFile.fromTreeUri(context, treeUri)
                        docFile?.delete()
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Failed deleting SAF root DocumentFile for ${novel.title}", e)
                    }
                }
            }

            // 3. Delete web novel downloaded directory if this is a web novel
            if (novel.isWebNovel) {
                val safeTitle = WebNovelDownloader.sanitizeFileName(novel.title)
                val dirCandidates = mutableListOf<File>()
                try {
                    val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    dirCandidates.add(File(File(publicDownloads, "NovelReader"), safeTitle))
                    dirCandidates.add(File(File(publicDownloads, "NovelReader"), novel.title))
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Error resolving public downloads candidate dir", e)
                }

                val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                if (extDir != null) {
                    dirCandidates.add(File(File(extDir, "NovelReader"), safeTitle))
                    dirCandidates.add(File(File(extDir, "NovelReader"), novel.title))
                }

                val internalRoot = File(context.filesDir, "NovelReader")
                dirCandidates.add(File(internalRoot, safeTitle))
                dirCandidates.add(File(internalRoot, novel.title))

                for (dir in dirCandidates) {
                    if (dir.exists()) {
                        dir.deleteRecursively()
                    }
                }
            }

            // 4. Delete any individual chapter files
            if (chapters != null) {
                for (ch in chapters) {
                    deleteSingleChapterFile(context, ch)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed during deleteNovelFilesFromDisk for ${novel.title}", e)
        }
    }
}
