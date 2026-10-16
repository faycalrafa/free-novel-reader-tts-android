package com.novelarrow.reader.data.userscript

import android.content.Context
import android.os.Environment
import android.util.Log
import com.novelarrow.reader.browser.engine.WebScriptInjector
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
import java.util.UUID

@Serializable
private data class UserscriptDatabaseState(
    val scripts: List<UserscriptEntity> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)

class UserscriptRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val dbFile: File
        get() = File(context.filesDir, "userscripts_db_v1.json")

    companion object {
        private const val TAG = "UserscriptRepo"
        private const val FOLDER_NAME = "NovelArrow_Scripts"
        private val repoMutex = Mutex()
        private var sharedFlowInternal: MutableStateFlow<List<UserscriptEntity>>? = null

        fun sanitizeFileName(name: String): String {
            val clean = name.trim().replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), "_")
            return clean.ifBlank { "script_" + System.currentTimeMillis() }
        }

        val DEFAULT_DEMO_SCRIPT = """
            // ==UserScript==
            // @name         Clean Novel Reader (Ad & Clutter Remover)
            // @version      1.0.0
            // @description  Removes floating ads, sticky banners, and declutters web novel pages for clean reading and TTS.
            // @match        *://*/*
            // @run-at       document-end
            // @grant        GM_notification
            // ==/UserScript==

            (function() {
                'use strict';
                const adSelectors = [
                    '.ads', '.ad-container', '.ad_box', '#sidebar', 'aside',
                    '.header-advert', '.footer-advert', 'iframe', '.floating-ad',
                    '.social-share', '.share-buttons', '.sticky-banner',
                    '.ad-wrapper', '#floating-banner', '.advertisement',
                    '[id*="ad_"]', '[class*="advert"]'
                ];
                let removed = 0;
                adSelectors.forEach(function(sel) {
                    document.querySelectorAll(sel).forEach(function(el) {
                        if (el.id === 'chapter-content' || el.classList.contains('chapter-content') || el.tagName === 'BODY') {
                            return;
                        }
                        el.remove();
                        removed++;
                    });
                });
                if (removed > 0 && typeof GM_notification === 'function') {
                    GM_notification('Novel page cleaned (' + removed + ' clutter items removed)');
                }
            })();
        """.trimIndent()
    }

    private val _scriptsFlow: MutableStateFlow<List<UserscriptEntity>>
    val scriptsFlow: StateFlow<List<UserscriptEntity>>

    init {
        val initialScripts = loadDatabase()
        synchronized(UserscriptRepository::class.java) {
            if (sharedFlowInternal == null) {
                sharedFlowInternal = MutableStateFlow(initialScripts)
            }
        }
        _scriptsFlow = sharedFlowInternal!!
        scriptsFlow = _scriptsFlow.asStateFlow()

        // Sync directory in background to detect files placed in Downloads
        try {
            ensureScriptsDirectory()
            val synced = syncFromDiskInternal()
            if (synced.isEmpty() && _scriptsFlow.value.isEmpty()) {
                seedDefaultDemoScript()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Initial sync failed: ${e.message}")
        }
    }

    private fun seedDefaultDemoScript() {
        try {
            val scriptsDir = ensureScriptsDirectory()
            val fileName = "Clean_Novel_Reader.user.js"
            val targetFile = File(scriptsDir, fileName)
            val demoCode = DEFAULT_DEMO_SCRIPT.trimIndent()
            targetFile.writeText(demoCode, Charsets.UTF_8)

            val parsed = UserscriptMetadataParser.parse(demoCode, fallbackName = "Clean Novel Reader (Ad & Clutter Remover)")
            val demoEntity = UserscriptEntity(
                id = UUID.randomUUID().toString(),
                name = parsed.name,
                version = parsed.version,
                filePath = targetFile.absolutePath,
                isEnabled = true,
                matchPatterns = parsed.matchPatterns,
                runAt = parsed.runAt,
                description = parsed.description,
                author = parsed.author,
                grantList = parsed.grantList,
                lastModified = targetFile.lastModified()
            )
            val current = _scriptsFlow.value.toMutableList()
            if (current.none { it.name == demoEntity.name }) {
                current.add(demoEntity)
                saveDatabase(current)
                _scriptsFlow.value = current
                Log.i(TAG, "Successfully seeded default demo userscript: ${demoEntity.name}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to seed demo userscript: ${e.message}")
        }
    }

    fun getScriptsDirectory(): File {
        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val target = File(publicDir, FOLDER_NAME)
        if (target.exists() && target.isDirectory && target.canWrite()) {
            return target
        }
        try {
            if (target.mkdirs() || target.exists()) {
                return target
            }
        } catch (_: Exception) {}

        // Fallback to app-specific external files dir
        val appExtDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val appTarget = File(appExtDir, FOLDER_NAME)
        if (!appTarget.exists()) appTarget.mkdirs()
        return appTarget
    }

    private fun ensureScriptsDirectory(): File {
        val dir = getScriptsDirectory()
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun getScripts(): List<UserscriptEntity> = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            _scriptsFlow.value
        }
    }

    suspend fun getEnabledScripts(): List<UserscriptEntity> = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            _scriptsFlow.value.filter { it.isEnabled }
        }
    }

    suspend fun getScriptsForUrl(url: String): List<UserscriptEntity> = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            _scriptsFlow.value.filter { script ->
                script.isEnabled && script.matchPatterns.any { pattern ->
                    WebScriptInjector.matchesPattern(url, pattern)
                }
            }
        }
    }

    suspend fun getScriptById(id: String): UserscriptEntity? = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            _scriptsFlow.value.firstOrNull { it.id == id }
        }
    }

    suspend fun getScriptCode(script: UserscriptEntity): String = withContext(Dispatchers.IO) {
        try {
            val file = File(script.filePath)
            if (file.exists() && file.isFile) {
                return@withContext file.readText(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read script code from ${script.filePath}: ${e.message}")
        }
        ""
    }

    suspend fun saveScript(
        code: String,
        preferredName: String? = null,
        existingId: String? = null
    ): UserscriptEntity = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            val parsed = UserscriptMetadataParser.parse(code, fallbackName = preferredName ?: "Untitled Script")
            val scriptName = preferredName?.ifBlank { null } ?: parsed.name
            val scriptsDir = ensureScriptsDirectory()

            val existing = if (existingId != null) {
                _scriptsFlow.value.firstOrNull { it.id == existingId }
            } else null

            val targetFile = if (existing != null && File(existing.filePath).exists()) {
                File(existing.filePath)
            } else {
                val safeName = sanitizeFileName(scriptName)
                var candidate = File(scriptsDir, "$safeName.user.js")
                var counter = 1
                while (candidate.exists() && existing == null) {
                    candidate = File(scriptsDir, "${safeName}_$counter.user.js")
                    counter++
                }
                candidate
            }

            // Write raw JavaScript to the .user.js file in Downloads/[AppName]_Scripts/
            targetFile.writeText(code, Charsets.UTF_8)

            val scriptEntity = UserscriptEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = scriptName,
                version = parsed.version,
                filePath = targetFile.absolutePath,
                isEnabled = existing?.isEnabled ?: true,
                matchPatterns = parsed.matchPatterns,
                runAt = parsed.runAt,
                description = parsed.description,
                author = parsed.author,
                grantList = parsed.grantList,
                lastModified = System.currentTimeMillis()
            )

            val current = _scriptsFlow.value
            val updated = current.filter { it.id != scriptEntity.id } + scriptEntity
            saveDatabase(updated)
            _scriptsFlow.value = updated
            scriptEntity
        }
    }

    suspend fun toggleScriptEnabled(id: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            val current = _scriptsFlow.value
            val target = current.firstOrNull { it.id == id } ?: return@withLock
            val updated = current.map {
                if (it.id == id) it.copy(isEnabled = isEnabled, lastModified = System.currentTimeMillis()) else it
            }
            saveDatabase(updated)
            _scriptsFlow.value = updated
        }
    }

    suspend fun deleteScript(id: String): Boolean = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            val current = _scriptsFlow.value
            val target = current.firstOrNull { it.id == id } ?: return@withLock false

            // 1. Delete physical file from Downloads folder
            try {
                val file = File(target.filePath)
                if (file.exists() && file.isFile) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete physical file ${target.filePath}: ${e.message}")
            }

            // 2. Remove record from database
            val updated = current.filter { it.id != id }
            saveDatabase(updated)
            _scriptsFlow.value = updated
            true
        }
    }

    suspend fun syncFromDisk(): List<UserscriptEntity> = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            syncFromDiskInternal()
        }
    }

    private fun syncFromDiskInternal(): List<UserscriptEntity> {
        val scriptsDir = ensureScriptsDirectory()
        val files = scriptsDir.listFiles { f -> f.isFile && f.name.endsWith(".user.js", ignoreCase = true) } ?: emptyArray()

        val current = _scriptsFlow.value
        val existingByPath = current.associateBy { it.filePath }
        val updatedList = mutableListOf<UserscriptEntity>()

        for (file in files) {
            val existing = existingByPath[file.absolutePath]
            if (existing != null) {
                // If file was modified since last check, refresh metadata
                if (file.lastModified() > existing.lastModified) {
                    val code = try { file.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
                    val parsed = UserscriptMetadataParser.parse(code, fallbackName = file.nameWithoutExtension.removeSuffix(".user"))
                    updatedList.add(
                        existing.copy(
                            name = parsed.name,
                            version = parsed.version,
                            matchPatterns = parsed.matchPatterns,
                            runAt = parsed.runAt,
                            description = parsed.description,
                            author = parsed.author,
                            grantList = parsed.grantList,
                            lastModified = file.lastModified()
                        )
                    )
                } else {
                    updatedList.add(existing)
                }
            } else {
                // Discovered new file dropped into directory
                val code = try { file.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
                val parsed = UserscriptMetadataParser.parse(code, fallbackName = file.nameWithoutExtension.removeSuffix(".user"))
                updatedList.add(
                    UserscriptEntity(
                        id = UUID.randomUUID().toString(),
                        name = parsed.name,
                        version = parsed.version,
                        filePath = file.absolutePath,
                        isEnabled = true,
                        matchPatterns = parsed.matchPatterns,
                        runAt = parsed.runAt,
                        description = parsed.description,
                        author = parsed.author,
                        grantList = parsed.grantList,
                        lastModified = file.lastModified()
                    )
                )
            }
        }

        saveDatabase(updatedList)
        _scriptsFlow.value = updatedList
        return updatedList
    }

    private fun loadDatabase(): List<UserscriptEntity> {
        return try {
            if (!dbFile.exists()) return emptyList()
            val text = dbFile.readText(Charsets.UTF_8)
            if (text.isBlank()) return emptyList()
            val state = json.decodeFromString<UserscriptDatabaseState>(text)
            state.scripts
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load database: ${e.message}")
            emptyList()
        }
    }

    private fun saveDatabase(scripts: List<UserscriptEntity>) {
        try {
            val state = UserscriptDatabaseState(scripts = scripts, lastUpdated = System.currentTimeMillis())
            val text = json.encodeToString(state)
            dbFile.writeText(text, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save database: ${e.message}")
        }
    }
}
