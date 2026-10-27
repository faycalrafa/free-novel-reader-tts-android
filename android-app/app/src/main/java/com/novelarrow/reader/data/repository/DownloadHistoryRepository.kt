package com.novelarrow.reader.data.repository

import android.content.Context
import androidx.core.util.AtomicFile
import com.novelarrow.reader.data.model.DownloadHistoryEntry
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

@Serializable
private data class DownloadHistoryDatabase(
    val entries: List<DownloadHistoryEntry> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)

class DownloadHistoryRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val dbFile: File
        get() = File(context.filesDir, "download_history_v1.json")

    private val atomicFile: AtomicFile
        get() = AtomicFile(dbFile)

    companion object {
        private const val TAG = "DownloadHistoryRepo"
        private const val MAX_ENTRIES = 1000
        private val repoMutex = Mutex()
        private var sharedHistoryFlow: MutableStateFlow<List<DownloadHistoryEntry>>? = null

        @androidx.annotation.VisibleForTesting
        fun resetInMemoryCacheForTesting() {
            synchronized(DownloadHistoryRepository::class.java) {
                sharedHistoryFlow = null
            }
        }
    }

    private val _historyFlow: MutableStateFlow<List<DownloadHistoryEntry>>
    val downloadHistoryFlow: StateFlow<List<DownloadHistoryEntry>>

    init {
        val initialEntries = loadHistorySync()
        synchronized(DownloadHistoryRepository::class.java) {
            if (sharedHistoryFlow == null) {
                sharedHistoryFlow = MutableStateFlow(initialEntries)
            }
        }
        _historyFlow = sharedHistoryFlow!!
        downloadHistoryFlow = _historyFlow.asStateFlow()
    }

    private fun loadHistorySync(): List<DownloadHistoryEntry> {
        return try {
            if (!dbFile.exists()) {
                emptyList()
            } else {
                val bytes = atomicFile.readFully()
                val content = String(bytes, Charsets.UTF_8)
                val db = json.decodeFromString<DownloadHistoryDatabase>(content)
                db.entries.sortedByDescending { it.timestamp }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to load download history from disk: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun recordDownload(entry: DownloadHistoryEntry) = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            try {
                val current = _historyFlow.value.toMutableList()
                // Prevent immediate duplicate entries for the exact same file path
                current.removeAll { it.filePath == entry.filePath }
                current.add(0, entry)

                val trimmed = if (current.size > MAX_ENTRIES) {
                    current.take(MAX_ENTRIES)
                } else {
                    current
                }

                _historyFlow.value = trimmed
                saveToDiskInternal(trimmed)
                AppLogger.d(TAG, "Recorded download: ${entry.fileName} (${entry.fileSizeBytes} bytes, isBatch=${entry.isBatchNovelChapter})")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error recording download entry: ${e.message}", e)
            }
        }
    }

    suspend fun deleteEntry(id: String) = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            try {
                val updated = _historyFlow.value.filterNot { it.id == id }
                _historyFlow.value = updated
                saveToDiskInternal(updated)
                AppLogger.d(TAG, "Deleted download history entry $id")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error deleting download history entry: ${e.message}", e)
            }
        }
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            try {
                _historyFlow.value = emptyList()
                saveToDiskInternal(emptyList())
                AppLogger.i(TAG, "Cleared all download history entries")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error clearing download history: ${e.message}", e)
            }
        }
    }

    private fun saveToDiskInternal(entries: List<DownloadHistoryEntry>) {
        var stream: FileOutputStream? = null
        try {
            val db = DownloadHistoryDatabase(entries = entries)
            val jsonText = json.encodeToString(db)
            stream = atomicFile.startWrite()
            stream.write(jsonText.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed writing download history atomically: ${e.message}", e)
            if (stream != null) {
                atomicFile.failWrite(stream)
            }
        }
    }
}
