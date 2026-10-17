package com.novelarrow.reader.data.model

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.novelarrow.reader.playback.NeuralModelDownloader
import com.novelarrow.reader.playback.SupportedLanguage
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object CustomVoiceManager {
    private const val TAG = "CustomVoiceManager"
    private const val CUSTOM_VOICES_FILE = "custom_neural_voices.json"

    fun getCustomVoices(context: Context): List<NeuralVoiceInfo> {
        val file = File(context.filesDir, CUSTOM_VOICES_FILE)
        if (!file.exists()) return emptyList()

        return try {
            val jsonArray = JSONArray(file.readText())
            val list = mutableListOf<NeuralVoiceInfo>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val langCode = obj.optString("language", "en")
                val lang = when (langCode.lowercase()) {
                    "ar" -> SupportedLanguage.ARABIC
                    "fr" -> SupportedLanguage.FRENCH
                    else -> SupportedLanguage.ENGLISH
                }
                list.add(
                    NeuralVoiceInfo(
                        id = obj.getString("id"),
                        engine = obj.getString("engine"),
                        name = obj.getString("name"),
                        gender = obj.optString("gender", "Neutral"),
                        quality = obj.optString("quality", "Custom Model"),
                        language = lang,
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        modelFileName = obj.getString("modelFileName"),
                        onnxDownloadUrl = "",
                        configDownloadUrl = null,
                        sampleAudioUrl = null,
                        description = obj.optString("description", "Imported custom neural model")
                    )
                )
            }
            list
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to load custom voices list: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun validateAndImportModel(
        context: Context,
        engine: String,
        voiceName: String,
        gender: String,
        language: SupportedLanguage,
        modelUri: Uri,
        configUri: Uri,
        dataDirUri: Uri?
    ): Result<NeuralVoiceInfo> = withContext(Dispatchers.IO) {
        try {
            if (voiceName.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Voice name cannot be empty."))
            }

            // 1. Validate ONNX Model file
            val modelName = getFileNameFromUri(context, modelUri) ?: "model.onnx"
            var modelSize = 0L
            context.contentResolver.openInputStream(modelUri)?.use { input ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    modelSize += bytesRead
                }
            } ?: return@withContext Result.failure(IllegalArgumentException("Cannot open model file stream."))

            if (modelSize < 100_000L) {
                return@withContext Result.failure(IllegalArgumentException("Model file is too small (${modelSize / 1024} KB). Valid ONNX models are typically > 1MB."))
            }

            // 2. Validate Config / Tokens file (must be valid JSON)
            val configText = context.contentResolver.openInputStream(configUri)?.use { input ->
                input.bufferedReader().readText()
            } ?: return@withContext Result.failure(IllegalArgumentException("Cannot open config/tokens file stream."))

            try {
                JSONObject(configText)
            } catch (e: Exception) {
                return@withContext Result.failure(IllegalArgumentException("Config/tokens file is not valid JSON: ${e.message}"))
            }

            // 3. Validate Data Directory (if provided, e.g. espeak-ng-data for Piper)
            if (dataDirUri != null) {
                val docDir = DocumentFile.fromTreeUri(context, dataDirUri)
                if (docDir == null || !docDir.isDirectory || !docDir.canRead()) {
                    return@withContext Result.failure(IllegalArgumentException("Selected data directory is unreadable or not a directory."))
                }
            }

            // 4. Copy files to app storage
            val targetDir = NeuralModelDownloader.getModelDirectory(context, engine)
            val safeBaseName = voiceName.trim().replace(Regex("[^a-zA-Z0-9_]"), "_").lowercase()
            val targetModelFileName = "custom_${safeBaseName}_${System.currentTimeMillis()}.onnx"
            val targetModelFile = File(targetDir, targetModelFileName)
            val targetConfigFile = File(targetDir, "$targetModelFileName.json")

            context.contentResolver.openInputStream(modelUri)?.use { input ->
                FileOutputStream(targetModelFile).use { output ->
                    input.copyTo(output)
                }
            }

            context.contentResolver.openInputStream(configUri)?.use { input ->
                FileOutputStream(targetConfigFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Copy data directory if provided
            if (dataDirUri != null) {
                val dataSubDir = File(targetDir, "custom_${safeBaseName}_data")
                dataSubDir.mkdirs()
                copyDocumentTree(context, dataDirUri, dataSubDir)
            }

            val newVoice = NeuralVoiceInfo(
                id = "custom_${engine.lowercase()}_${safeBaseName}_${System.currentTimeMillis()}",
                engine = engine.uppercase(),
                name = "$voiceName (Custom)",
                gender = gender,
                quality = "Custom",
                language = language,
                sizeBytes = targetModelFile.length(),
                modelFileName = targetModelFileName,
                onnxDownloadUrl = "",
                configDownloadUrl = null,
                sampleAudioUrl = null,
                description = "User-imported $engine voice model (${targetModelFile.length() / (1024 * 1024)} MB)"
            )

            // Save to custom voices list
            saveCustomVoice(context, newVoice)
            AppLogger.i(TAG, "Successfully imported custom voice: ${newVoice.name} ($targetModelFileName)")
            Result.success(newVoice)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error importing custom neural model: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun saveCustomVoice(context: Context, voice: NeuralVoiceInfo) {
        val existing = getCustomVoices(context).toMutableList()
        existing.removeAll { it.id == voice.id }
        existing.add(voice)

        val jsonArray = JSONArray()
        for (v in existing) {
            val obj = JSONObject().apply {
                put("id", v.id)
                put("engine", v.engine)
                put("name", v.name)
                put("gender", v.gender)
                put("quality", v.quality)
                put("language", v.language.code)
                put("sizeBytes", v.sizeBytes)
                put("modelFileName", v.modelFileName)
                put("description", v.description)
            }
            jsonArray.put(obj)
        }

        val file = File(context.filesDir, CUSTOM_VOICES_FILE)
        file.writeText(jsonArray.toString(2))
    }

    fun deleteCustomVoice(context: Context, voiceId: String): Boolean {
        val existing = getCustomVoices(context).toMutableList()
        val voiceToDelete = existing.firstOrNull { it.id == voiceId } ?: return false

        NeuralModelDownloader.deleteModel(context, voiceToDelete)
        existing.removeAll { it.id == voiceId }

        val jsonArray = JSONArray()
        for (v in existing) {
            val obj = JSONObject().apply {
                put("id", v.id)
                put("engine", v.engine)
                put("name", v.name)
                put("gender", v.gender)
                put("quality", v.quality)
                put("language", v.language.code)
                put("sizeBytes", v.sizeBytes)
                put("modelFileName", v.modelFileName)
                put("description", v.description)
            }
            jsonArray.put(obj)
        }

        val file = File(context.filesDir, CUSTOM_VOICES_FILE)
        file.writeText(jsonArray.toString(2))
        AppLogger.i(TAG, "Deleted custom voice $voiceId from registry")
        return true
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) name = cursor.getString(index)
                }
            }
        }
        if (name == null) {
            name = uri.path?.let { File(it).name }
        }
        return name
    }

    private fun copyDocumentTree(context: Context, treeUri: Uri, destinationDir: File) {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return
        copyDocRecursive(context, rootDoc, destinationDir)
    }

    private fun copyDocRecursive(context: Context, doc: DocumentFile, destDir: File) {
        if (doc.isDirectory) {
            val sub = File(destDir, doc.name ?: "subdir")
            sub.mkdirs()
            doc.listFiles().forEach { child ->
                copyDocRecursive(context, child, sub)
            }
        } else if (doc.isFile) {
            val target = File(destDir, doc.name ?: "file")
            context.contentResolver.openInputStream(doc.uri)?.use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
