package com.novelarrow.reader.data.db

import android.content.Context
import androidx.room.withTransaction
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.util.AppLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class LegacyLibraryDatabase(
    val novels: List<Novel> = emptyList(),
    val chapters: Map<String, List<Chapter>> = emptyMap(),
    val lastActiveNovelId: String? = null
)

object DatabaseMigrator {

    private const val TAG = "DatabaseMigrator"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun ingestLegacyJsonIfNeeded(context: Context, database: AppDatabase): Boolean {
        try {
            val novelCount = database.novelDao().getNovelCount()
            if (novelCount > 0) {
                AppLogger.d(TAG, "Room database already contains $novelCount novels. Skipping legacy JSON ingestion.")
                return false
            }

            val legacyFiles = listOf(
                File(context.filesDir, "novel_library_v1.json"),
                File(context.filesDir, "library_db.json")
            )

            val legacyFile = legacyFiles.firstOrNull { it.exists() && it.length() > 0 }
            if (legacyFile == null) {
                AppLogger.d(TAG, "No legacy JSON database file found to migrate.")
                return false
            }

            AppLogger.i(TAG, "Legacy database detected at ${legacyFile.absolutePath} (${legacyFile.length()} bytes). Starting migration...")
            val startTime = System.currentTimeMillis()

            val text = legacyFile.readText(Charsets.UTF_8)
            if (text.isBlank()) {
                AppLogger.w(TAG, "Legacy database file is blank. Skipping migration.")
                return false
            }

            val legacyDb = json.decodeFromString<LegacyLibraryDatabase>(text)

            var totalChaptersCount = 0
            database.withTransaction {
                val novelEntities = legacyDb.novels.map { it.toEntity() }
                if (novelEntities.isNotEmpty()) {
                    database.novelDao().insertNovels(novelEntities)
                }

                val allChapters = legacyDb.chapters.values.flatten().map { it.toEntity() }
                totalChaptersCount = allChapters.size
                if (allChapters.isNotEmpty()) {
                    database.chapterDao().insertChapters(allChapters)
                }

                legacyDb.lastActiveNovelId?.let { lastId ->
                    database.appMetaDao().setMeta(AppMetaEntity("lastActiveNovelId", lastId))
                }
            }

            val durationMs = System.currentTimeMillis() - startTime
            AppLogger.i(
                TAG,
                "Successfully migrated ${legacyDb.novels.size} novels and $totalChaptersCount chapters into Room in ${durationMs}ms."
            )

            try {
                val backupFile = File(legacyFile.parentFile, "${legacyFile.name}.bak")
                if (legacyFile.renameTo(backupFile)) {
                    AppLogger.i(TAG, "Renamed legacy file to ${backupFile.name}")
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Could not rename legacy file to .bak: ${e.message}", e)
            }

            return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to ingest legacy JSON database into Room: ${e.message}", e)
            return false
        }
    }
}
