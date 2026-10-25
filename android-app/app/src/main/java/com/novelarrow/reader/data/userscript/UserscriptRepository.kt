package com.novelarrow.reader.data.userscript

import android.content.Context
import com.novelarrow.reader.browser.engine.WebScriptInjector
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
        private val repoMutex = Mutex()
        private var sharedFlowInternal: MutableStateFlow<List<UserscriptEntity>>? = null

        fun sanitizeFileName(name: String): String = UserscriptFileManager.sanitizeFileName(name)

        val DEFAULT_DEMO_SCRIPT: String
            get() = DefaultUserscriptTemplates.DEFAULT_DEMO_SCRIPT

        val DEFAULT_MULTI_DOWNLOAD_SCRIPT: String
            get() = DefaultUserscriptTemplates.DEFAULT_MULTI_DOWNLOAD_SCRIPT
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
            UserscriptFileManager.ensureScriptsDirectory(context)
            syncFromDiskInternal()
            seedDefaultScripts()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Initial sync failed: ${e.message}", e)
        }
    }

    private fun seedDefaultScripts() {
        seedScriptFile("Clean_Novel_Reader.user.js", DEFAULT_DEMO_SCRIPT, "Clean Novel Reader (Ad & Clutter Remover)")
        seedScriptFile("Multi-Download.user.js", DEFAULT_MULTI_DOWNLOAD_SCRIPT, "Multi-Download")
    }

    private fun seedScriptFile(fileName: String, scriptCode: String, fallbackName: String) {
        try {
            val scriptsDir = UserscriptFileManager.ensureScriptsDirectory(context)
            val targetFile = File(scriptsDir, fileName)
            val parsed = UserscriptMetadataParser.parse(scriptCode.trimIndent(), fallbackName = fallbackName)
            if (!targetFile.exists() || (fileName == "Multi-Download.user.js" && !targetFile.readText().contains("@version      3.0.0"))) {
                targetFile.writeText(scriptCode.trimIndent(), Charsets.UTF_8)
            }

            val current = _scriptsFlow.value.toMutableList()
            val existingIndex = current.indexOfFirst { it.name == parsed.name || it.name == fallbackName }
            if (existingIndex == -1) {
                val entity = UserscriptEntity(
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
                current.add(entity)
                saveDatabase(current)
                _scriptsFlow.value = current
                AppLogger.i(TAG, "Successfully seeded default userscript: ${entity.name}")
            } else if (fileName == "Multi-Download.user.js" && current[existingIndex].version != parsed.version) {
                current[existingIndex] = current[existingIndex].copy(
                    version = parsed.version,
                    description = parsed.description,
                    grantList = parsed.grantList,
                    matchPatterns = parsed.matchPatterns,
                    lastModified = targetFile.lastModified()
                )
                saveDatabase(current)
                _scriptsFlow.value = current
                AppLogger.i(TAG, "Successfully upgraded default userscript: ${parsed.name} to v${parsed.version}")
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to seed default userscript $fileName: ${e.message}", e)
        }
    }

    fun getScriptsDirectory(): File = UserscriptFileManager.getScriptsDirectory(context)

    suspend fun getScripts(): List<UserscriptEntity> = withContext(Dispatchers.IO) {
        repoMutex.withLock { _scriptsFlow.value }
    }

    suspend fun getEnabledScripts(): List<UserscriptEntity> = withContext(Dispatchers.IO) {
        repoMutex.withLock { _scriptsFlow.value.filter { it.isEnabled } }
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
        repoMutex.withLock { _scriptsFlow.value.firstOrNull { it.id == id } }
    }

    suspend fun getScriptCode(script: UserscriptEntity): String = withContext(Dispatchers.IO) {
        UserscriptFileManager.readScriptCode(script.filePath)
    }

    suspend fun saveScript(
        code: String,
        preferredName: String? = null,
        existingId: String? = null
    ): UserscriptEntity = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            val parsed = UserscriptMetadataParser.parse(code, fallbackName = preferredName ?: "Untitled Script")
            val scriptName = if (parsed.name.isNotBlank() && parsed.name != "Untitled Script" && parsed.name != "Userscript") {
                parsed.name
            } else {
                preferredName?.ifBlank { null } ?: parsed.name
            }
            val scriptsDir = UserscriptFileManager.ensureScriptsDirectory(context)

            val existing = if (existingId != null) {
                _scriptsFlow.value.firstOrNull { it.id == existingId }
            } else null

            val targetFile = if (existing != null && File(existing.filePath).exists()) {
                val currentFile = File(existing.filePath)
                val safeName = sanitizeFileName(scriptName)
                val expectedFileName = "$safeName.user.js"
                if (!currentFile.name.equals(expectedFileName, ignoreCase = true)) {
                    val renamedFile = File(scriptsDir, expectedFileName)
                    if (!renamedFile.exists()) {
                        currentFile.renameTo(renamedFile)
                        renamedFile
                    } else {
                        currentFile
                    }
                } else {
                    currentFile
                }
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

            UserscriptFileManager.deletePhysicalScriptFile(target.filePath)

            val updated = current.filter { it.id != id }
            saveDatabase(updated)
            _scriptsFlow.value = updated
            true
        }
    }

    suspend fun renameScript(id: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return@withContext false
        repoMutex.withLock {
            val list = _scriptsFlow.value.toMutableList()
            val index = list.indexOfFirst { it.id == id }
            if (index == -1) return@withContext false
            val oldEntity = list[index]
            try {
                val file = File(oldEntity.filePath)
                var newFilePath = oldEntity.filePath
                val scriptsDir = UserscriptFileManager.ensureScriptsDirectory(context)
                val newFileName = "${sanitizeFileName(trimmed)}.user.js"
                val newFile = File(scriptsDir, newFileName)
                if (file.exists() && file.isFile) {
                    val code = file.readText(Charsets.UTF_8)
                    val updatedCode = if (code.contains("// @name")) {
                        code.replace(Regex("""//\s*@name\s+.*"""), "// @name         $trimmed")
                    } else {
                        "// @name         $trimmed\n$code"
                    }
                    if (file.absolutePath != newFile.absolutePath) {
                        file.delete()
                    }
                    newFile.writeText(updatedCode, Charsets.UTF_8)
                    newFilePath = newFile.absolutePath
                }
                val updatedEntity = oldEntity.copy(name = trimmed, filePath = newFilePath, lastModified = System.currentTimeMillis())
                list[index] = updatedEntity
                saveDatabase(list)
                _scriptsFlow.value = list
                AppLogger.i(TAG, "Renamed script ${oldEntity.name} to $trimmed (file: $newFilePath)")
                true
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to rename script ${oldEntity.name} to $trimmed: ${e.message}", e)
                false
            }
        }
    }

    suspend fun syncFromDisk(): List<UserscriptEntity> = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            syncFromDiskInternal()
        }
    }

    private fun syncFromDiskInternal(): List<UserscriptEntity> {
        val scriptsDir = UserscriptFileManager.ensureScriptsDirectory(context)
        val files = scriptsDir.listFiles { f -> f.isFile && f.name.endsWith(".user.js", ignoreCase = true) } ?: emptyArray()

        val current = _scriptsFlow.value
        val existingByPath = current.associateBy { it.filePath }
        val updatedList = mutableListOf<UserscriptEntity>()

        for (file in files) {
            val existing = existingByPath[file.absolutePath]
            if (existing != null) {
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
            AppLogger.w(TAG, "Failed to load database: ${e.message}", e)
            emptyList()
        }
    }

    private fun saveDatabase(scripts: List<UserscriptEntity>) {
        try {
            val state = UserscriptDatabaseState(scripts = scripts, lastUpdated = System.currentTimeMillis())
            val text = json.encodeToString(state)
            dbFile.writeText(text, Charsets.UTF_8)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save database: ${e.message}", e)
        }
    }
}
