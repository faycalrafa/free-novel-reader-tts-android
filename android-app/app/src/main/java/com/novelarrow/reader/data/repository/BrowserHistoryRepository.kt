package com.novelarrow.reader.data.repository

import android.content.Context
import androidx.core.util.AtomicFile
import com.novelarrow.reader.data.model.BrowserHistoryEntry
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
private data class BrowserHistoryDatabase(
    val entries: List<BrowserHistoryEntry> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)

class BrowserHistoryRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val dbFile: File
        get() = File(context.filesDir, "browser_history_v1.json")

    private val atomicFile: AtomicFile
        get() = AtomicFile(dbFile)

    companion object {
        private const val TAG = "BrowserHistoryRepo"
        private const val MAX_HISTORY_ENTRIES = 500
        private val repoMutex = Mutex()
        private var sharedHistoryFlow: MutableStateFlow<List<BrowserHistoryEntry>>? = null

        fun resetInMemoryCacheForTesting() {
            synchronized(BrowserHistoryRepository::class.java) {
                sharedHistoryFlow = null
            }
        }
    }

    private val _historyFlow: MutableStateFlow<List<BrowserHistoryEntry>>
    val historyFlow: StateFlow<List<BrowserHistoryEntry>>

    init {
        val initialEntries = loadHistorySync()
        synchronized(BrowserHistoryRepository::class.java) {
            if (sharedHistoryFlow == null) {
                sharedHistoryFlow = MutableStateFlow(initialEntries)
            }
        }
        _historyFlow = sharedHistoryFlow!!
        historyFlow = _historyFlow.asStateFlow()
    }

    private fun loadHistorySync(): List<BrowserHistoryEntry> {
        return try {
            if (!dbFile.exists()) {
                emptyList()
            } else {
                val bytes = atomicFile.readFully()
                val content = String(bytes, Charsets.UTF_8)
                val db = json.decodeFromString<BrowserHistoryDatabase>(content)
                db.entries.sortedByDescending { it.timestamp }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to load browser history from disk: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun recordVisit(url: String, title: String, tabId: String = "") = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank() || trimmedUrl == "about:blank") return@withContext
        val cleanTitle = title.trim().ifBlank {
            if (trimmedUrl.contains("google")) "Google" else trimmedUrl
        }

        repoMutex.withLock {
            try {
                val current = _historyFlow.value.toMutableList()
                // Remove immediate previous visit to the exact same URL in the same tab to prevent spamming
                val first = current.firstOrNull()
                if (first != null && first.url == trimmedUrl && first.tabId == tabId) {
                    // Update timestamp & title of the latest entry
                    current[0] = first.copy(title = cleanTitle, timestamp = System.currentTimeMillis())
                } else {
                    current.add(0, BrowserHistoryEntry(tabId = tabId, url = trimmedUrl, title = cleanTitle))
                }

                val trimmed = if (current.size > MAX_HISTORY_ENTRIES) {
                    current.take(MAX_HISTORY_ENTRIES)
                } else {
                    current
                }

                saveHistoryToDisk(trimmed)
                _historyFlow.value = trimmed
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to record browser visit: ${e.message}", e)
            }
        }
    }

    suspend fun deleteEntry(id: String) = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            try {
                val updated = _historyFlow.value.filterNot { it.id == id }
                saveHistoryToDisk(updated)
                _historyFlow.value = updated
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to delete browser history entry $id: ${e.message}", e)
            }
        }
    }

    suspend fun clearHistoryForTab(tabId: String) = withContext(Dispatchers.IO) {
        if (tabId.isBlank()) return@withContext
        repoMutex.withLock {
            try {
                val updated = _historyFlow.value.filterNot { it.tabId == tabId }
                saveHistoryToDisk(updated)
                _historyFlow.value = updated
                AppLogger.i(TAG, "Cleared browsing history for tab: $tabId")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to clear history for tab $tabId: ${e.message}", e)
            }
        }
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            try {
                val empty = emptyList<BrowserHistoryEntry>()
                saveHistoryToDisk(empty)
                _historyFlow.value = empty
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to clear browser history: ${e.message}", e)
            }
        }
    }

    private fun saveHistoryToDisk(entries: List<BrowserHistoryEntry>) {
        val db = BrowserHistoryDatabase(entries = entries, lastUpdated = System.currentTimeMillis())
        val serialized = json.encodeToString(db)
        var stream: FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(serialized.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to write browser history atomically: ${e.message}", e)
            if (stream != null) {
                atomicFile.failWrite(stream)
            }
        }
    }
}
