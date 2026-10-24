package com.novelarrow.reader.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.novelarrow.reader.data.util.FileSchemeUtils
import com.novelarrow.reader.util.AppLogger
import java.io.File
import java.util.Locale

internal data class ScannedFileItem(
    val uriStr: String,
    val displayName: String,
    val sizeBytes: Long
)

internal object SafTreeScanner {

    private const val TAG = "SafTreeScanner"

    val supportedExtensions get() = FileSchemeUtils.SUPPORTED_EXTENSIONS

    fun isSupported(fileName: String?): Boolean = FileSchemeUtils.isSupportedFile(fileName)

    fun querySafChildrenFast(
        context: Context,
        treeUri: Uri,
        parentDocId: String
    ): Pair<List<Pair<String, String>>, List<ScannedFileItem>> {
        val subdirs = mutableListOf<Pair<String, String>>()
        val files = mutableListOf<ScannedFileItem>()
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                while (cursor.moveToNext()) {
                    val docId = (if (idIdx >= 0) cursor.getString(idIdx) else null) ?: continue
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else "Untitled"
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else ""
                    val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        subdirs.add(Pair(docId, name))
                    } else if (isSupported(name)) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString()
                        files.add(ScannedFileItem(docUri, name, size))
                    }
                }
            }
        } catch (e: SecurityException) {
            AppLogger.w(TAG, "SAF permission denied querying children for parentDocId $parentDocId: ${e.message}", e)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to query SAF children for parentDocId $parentDocId: ${e.message}", e)
        }
        return Pair(subdirs, files)
    }

    fun collectSafChildrenRecursivelyFast(
        context: Context,
        treeUri: Uri,
        dirDocId: String,
        out: MutableList<ScannedFileItem>
    ) {
        val (subdirs, files) = querySafChildrenFast(context, treeUri, dirDocId)
        for (file in files) {
            out.add(file)
        }
        for ((subDocId, _) in subdirs) {
            collectSafChildrenRecursivelyFast(context, treeUri, subDocId, out)
        }
    }

    fun scanDocumentFileFallback(rootDoc: DocumentFile): Pair<Map<String, List<ScannedFileItem>>, List<ScannedFileItem>> {
        val subdirMap = mutableMapOf<String, List<ScannedFileItem>>()
        val looseFiles = mutableListOf<ScannedFileItem>()
        for (f in rootDoc.listFiles()) {
            val name = f.name ?: continue
            if (f.isDirectory) {
                val list = mutableListOf<ScannedFileItem>()
                collectDocumentFiles(f, list)
                if (list.isNotEmpty()) subdirMap[name] = list
            } else if (f.isFile && isSupported(name)) {
                looseFiles.add(ScannedFileItem(f.uri.toString(), name, f.length()))
            }
        }
        return Pair(subdirMap, looseFiles)
    }

    fun collectDocumentFiles(dir: DocumentFile, out: MutableList<ScannedFileItem>) {
        for (f in dir.listFiles()) {
            val name = f.name ?: continue
            if (f.isDirectory) {
                collectDocumentFiles(f, out)
            } else if (f.isFile && isSupported(name)) {
                out.add(ScannedFileItem(f.uri.toString(), name, f.length()))
            }
        }
    }

    fun scanLocalDirectory(dir: File): Pair<Map<String, List<ScannedFileItem>>, List<ScannedFileItem>> {
        val subdirMap = mutableMapOf<String, List<ScannedFileItem>>()
        val looseFiles = mutableListOf<ScannedFileItem>()
        val children = dir.listFiles() ?: return Pair(subdirMap, looseFiles)
        for (f in children) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                val list = mutableListOf<ScannedFileItem>()
                collectLocalFiles(f, list)
                if (list.isNotEmpty()) subdirMap[f.name] = list
            } else if (f.isFile && isSupported(f.name)) {
                looseFiles.add(ScannedFileItem(f.absolutePath, f.name, f.length()))
            }
        }
        return Pair(subdirMap, looseFiles)
    }

    fun collectLocalFiles(dir: File, out: MutableList<ScannedFileItem>) {
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                collectLocalFiles(f, out)
            } else if (f.isFile && isSupported(f.name)) {
                out.add(ScannedFileItem(f.absolutePath, f.name, f.length()))
            }
        }
    }
}
