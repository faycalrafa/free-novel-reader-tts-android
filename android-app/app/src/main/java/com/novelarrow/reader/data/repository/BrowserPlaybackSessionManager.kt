package com.novelarrow.reader.data.repository

import android.content.Context
import androidx.core.util.AtomicFile
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

@Serializable
data class BrowserPlaybackSession(
    val url: String = "",
    val chapterTitle: String = "",
    val novelTitle: String = "",
    val paragraphIndex: Int = 0,
    val isReaderMode: Boolean = false,
    val totalParagraphs: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

object BrowserPlaybackSessionManager {

    private const val TAG = "BrowserPlaybackSession"
    private const val PREFS_NAME = "browser_session_prefs"
    private const val KEY_LAST_SOURCE = "last_playback_source"
    private const val KEY_DISMISSED_TIMESTAMP = "dismissed_session_timestamp"

    private const val SESSION_FILE_NAME = "browser_last_session.json"
    private const val OFFLINE_HTML_FILE_NAME = "browser_offline_session.html"

    const val SOURCE_BROWSER = "BROWSER"
    const val SOURCE_READER = "READER"

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Volatile
    private var cachedSession: BrowserPlaybackSession? = null

    private var lastSaveTimeMs = 0L
    private const val SAVE_DEBOUNCE_MS = 1000L

    fun getLastPlaybackSource(context: Context): String {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_LAST_SOURCE, SOURCE_READER) ?: SOURCE_READER
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error reading last playback source: ${e.message}", e)
            SOURCE_READER
        }
    }

    fun setLastPlaybackSource(context: Context, source: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_LAST_SOURCE, source).apply()
            AppLogger.d(TAG, "Updated last playback source to: $source")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update last playback source: ${e.message}", e)
        }
    }

    fun loadLastSession(context: Context): BrowserPlaybackSession? {
        cachedSession?.let { return it }
        val file = File(context.filesDir, SESSION_FILE_NAME)
        if (!file.exists() || file.length() == 0L) return null

        return try {
            val text = file.readText(Charsets.UTF_8)
            val session = json.decodeFromString<BrowserPlaybackSession>(text)
            cachedSession = session
            session
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to deserialize browser playback session: ${e.message}", e)
            null
        }
    }

    private val managerScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    fun getLastSession(context: Context): BrowserPlaybackSession? = loadLastSession(context)

    fun saveProgress(
        context: Context,
        url: String,
        chapterTitle: String,
        paragraphIndex: Int,
        totalParagraphs: Int = 0,
        isReaderMode: Boolean = false,
        novelTitle: String = "",
        forceImmediate: Boolean = false
    ) {
        managerScope.launch {
            saveProgressInternal(
                context = context,
                url = url,
                chapterTitle = chapterTitle,
                novelTitle = novelTitle,
                paragraphIndex = paragraphIndex,
                isReaderMode = isReaderMode,
                totalParagraphs = totalParagraphs,
                forceImmediate = forceImmediate
            )
        }
    }

    suspend fun saveProgressInternal(
        context: Context,
        url: String,
        chapterTitle: String,
        novelTitle: String = "",
        paragraphIndex: Int,
        isReaderMode: Boolean,
        totalParagraphs: Int = 0,
        forceImmediate: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceImmediate && now - lastSaveTimeMs < SAVE_DEBOUNCE_MS) {
            // Still update cached session in memory
            cachedSession = cachedSession?.copy(
                url = url.ifBlank { cachedSession?.url ?: "" },
                chapterTitle = chapterTitle.ifBlank { cachedSession?.chapterTitle ?: "" },
                novelTitle = novelTitle.ifBlank { cachedSession?.novelTitle ?: "" },
                paragraphIndex = paragraphIndex,
                isReaderMode = isReaderMode,
                totalParagraphs = if (totalParagraphs > 0) totalParagraphs else cachedSession?.totalParagraphs ?: 0,
                timestamp = now
            )
            return@withContext
        }
        lastSaveTimeMs = now

        val session = BrowserPlaybackSession(
            url = url.ifBlank { cachedSession?.url ?: "" },
            chapterTitle = chapterTitle.ifBlank { cachedSession?.chapterTitle ?: "" },
            novelTitle = novelTitle.ifBlank { cachedSession?.novelTitle ?: "" },
            paragraphIndex = paragraphIndex,
            isReaderMode = isReaderMode,
            totalParagraphs = if (totalParagraphs > 0) totalParagraphs else cachedSession?.totalParagraphs ?: 0,
            timestamp = now
        )
        cachedSession = session
        setLastPlaybackSource(context, SOURCE_BROWSER)

        val targetFile = File(context.filesDir, SESSION_FILE_NAME)
        val atomicFile = AtomicFile(targetFile)
        var stream: FileOutputStream? = null
        try {
            val serialized = json.encodeToString(session)
            stream = atomicFile.startWrite()
            stream.write(serialized.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
            AppLogger.d(TAG, "Saved browser playback session: ${session.chapterTitle} @ para ${session.paragraphIndex}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error saving browser playback session: ${e.message}", e)
            if (stream != null) {
                atomicFile.failWrite(stream)
            }
        }
    }

    suspend fun saveOfflineHtml(
        context: Context,
        cleanHtml: String
    ) = withContext(Dispatchers.IO) {
        if (cleanHtml.isBlank()) return@withContext
        val targetFile = File(context.filesDir, OFFLINE_HTML_FILE_NAME)
        val atomicFile = AtomicFile(targetFile)
        var stream: FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(cleanHtml.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
            AppLogger.i(TAG, "Saved offline browser chapter page (${cleanHtml.length} bytes)")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed saving offline browser HTML page: ${e.message}", e)
            if (stream != null) {
                atomicFile.failWrite(stream)
            }
        }
    }

    fun getOfflineHtml(context: Context): String? {
        val file = File(context.filesDir, OFFLINE_HTML_FILE_NAME)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed reading offline browser HTML: ${e.message}", e)
            null
        }
    }

    fun hasOfflineSession(context: Context): Boolean {
        val session = loadLastSession(context) ?: return false
        if (session.url.isBlank() && session.chapterTitle.isBlank()) return false
        val html = getOfflineHtml(context)
        return !html.isNullOrBlank()
    }

    fun shouldProposeResume(context: Context): Boolean {
        if (getLastPlaybackSource(context) != SOURCE_BROWSER) return false
        val session = loadLastSession(context) ?: return false
        if (!hasOfflineSession(context)) return false

        // Within 30 days
        val maxAgeMs = 30L * 24 * 60 * 60 * 1000
        if (System.currentTimeMillis() - session.timestamp > maxAgeMs) return false

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dismissedTimestamp = prefs.getLong(KEY_DISMISSED_TIMESTAMP, 0L)
        return dismissedTimestamp < session.timestamp
    }

    fun dismissPrompt(context: Context, session: BrowserPlaybackSession) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_DISMISSED_TIMESTAMP, session.timestamp).apply()
            AppLogger.d(TAG, "Dismissed resume prompt for session timestamp ${session.timestamp}")
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error dismissing session prompt: ${e.message}", e)
        }
    }
}
