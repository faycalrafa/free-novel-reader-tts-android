package com.novelarrow.reader.playback

import android.content.Context
import com.novelarrow.reader.data.model.NeuralVoiceInfo
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

object NeuralModelDownloader {
    private const val TAG = "NeuralModelDownloader"
    private const val BUFFER_SIZE = 8192
    private const val MAX_REDIRECTS = 5

    fun getModelDirectory(context: Context, engine: String): File {
        val extDir = context.getExternalFilesDir("neural_tts")?.let { File(it, engine.lowercase()) }
        if (extDir != null) {
            if (!extDir.exists()) extDir.mkdirs()
            return extDir
        }
        val dir = File(context.filesDir, "neural_tts/${engine.lowercase()}")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun findExistingModelFile(context: Context, voiceInfo: NeuralVoiceInfo): File? {
        val internalDir = File(context.filesDir, "neural_tts/${voiceInfo.engine.lowercase()}")
        val externalDir = context.getExternalFilesDir("neural_tts")?.let { File(it, voiceInfo.engine.lowercase()) }
        val defaultModelDir = File(context.filesDir, "vits-piper-en_US-lessac-medium")

        val searchDirs = listOfNotNull(externalDir, internalDir, defaultModelDir, context.filesDir)

        val namesToCheck = mutableListOf(voiceInfo.modelFileName)
        when (voiceInfo.engine.uppercase()) {
            "KITTEN" -> {
                namesToCheck.addAll(listOf(
                    "kitten_tts_micro_v0_8.onnx",
                    "kitten_tts_micro_0.8.onnx",
                    "kitten-nano-female.onnx",
                    "kitten-nano-male.onnx",
                    "kitten-micro-expressive.onnx",
                    "kitten-nano-clear.onnx",
                    "model.onnx"
                ))
            }
            "KOKORO" -> {
                namesToCheck.addAll(listOf(
                    "kokoro-v1.0-q8.onnx",
                    "kokoro-v0_19.onnx",
                    "model_q8.onnx",
                    "model.onnx"
                ))
            }
            "PIPER" -> {
                namesToCheck.add(voiceInfo.modelFileName.replace("-", "_"))
            }
        }

        for (dir in searchDirs) {
            if (!dir.exists()) continue
            for (name in namesToCheck) {
                val candidate = File(dir, name)
                if (candidate.exists() && candidate.length() > 1024L) {
                    return candidate
                }
            }
        }
        return null
    }

    fun getModelFile(context: Context, voiceInfo: NeuralVoiceInfo): File {
        val primary = File(getModelDirectory(context, voiceInfo.engine), voiceInfo.modelFileName)
        if (primary.exists() && primary.length() > 1024L) return primary

        val existing = findExistingModelFile(context, voiceInfo)
        if (existing != null && existing.exists() && existing.length() > 1024L) {
            // If located elsewhere, copy or return existing
            if (existing.name != primary.name || existing.parentFile != primary.parentFile) {
                try {
                    existing.copyTo(primary, overwrite = true)
                    AppLogger.i(TAG, "Migrated existing neural model from ${existing.absolutePath} to ${primary.absolutePath}")
                    return primary
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Using existing model file directly: ${existing.absolutePath} (${e.message})")
                    return existing
                }
            }
            return existing
        }
        return primary
    }

    fun isModelDownloaded(context: Context, voiceInfo: NeuralVoiceInfo): Boolean {
        val file = getModelFile(context, voiceInfo)
        val candidate = if (file.exists() && file.length() > 1024L) {
            file
        } else {
            val existing = findExistingModelFile(context, voiceInfo)
            if (existing != null && existing.exists() && existing.length() > 1024L) existing else null
        }

        if (candidate == null) {
            return try {
                context.assets.open("neural/${voiceInfo.modelFileName}").use { true }
            } catch (_: Exception) {
                false
            }
        }

        // Validate multi-file dependencies based on engine type
        val parentDir = candidate.parentFile ?: return false
        return when (voiceInfo.engine.uppercase()) {
            "KITTEN" -> {
                val voices = File(parentDir, "voices.bin")
                val tokens = File(parentDir, "tokens.txt")
                voices.exists() && voices.length() > 100L && tokens.exists() && tokens.length() > 10L
            }
            "KOKORO" -> {
                val voices = File(parentDir, "voices.bin")
                val tokens = File(parentDir, "tokens.txt")
                voices.exists() && voices.length() > 100L && tokens.exists() && tokens.length() > 10L
            }
            "PIPER" -> {
                val tokens = File(parentDir, "tokens.txt")
                val defaultTokens = File(context.filesDir, "vits-piper-en_US-lessac-medium/tokens.txt")
                (tokens.exists() && tokens.length() > 10L) || (defaultTokens.exists() && defaultTokens.length() > 10L)
            }
            else -> true
        }
    }

    fun deleteModel(context: Context, voiceInfo: NeuralVoiceInfo): Boolean {
        val file = getModelFile(context, voiceInfo)
        val configFile = File(file.parentFile, "${voiceInfo.modelFileName}.json")
        var deleted = false
        if (file.exists()) {
            deleted = file.delete()
            AppLogger.i(TAG, "Deleted neural model file: ${file.name}")
        }
        if (configFile.exists()) {
            configFile.delete()
            AppLogger.i(TAG, "Deleted neural config file: ${configFile.name}")
        }

        // Also check legacy/internal location if deleted
        val internalFile = File(context.filesDir, "neural_tts/${voiceInfo.engine.lowercase()}/${voiceInfo.modelFileName}")
        if (internalFile.exists()) {
            internalFile.delete()
        }
        return deleted
    }

    suspend fun downloadVoiceModel(
        context: Context,
        voiceInfo: NeuralVoiceInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val targetDir = getModelDirectory(context, voiceInfo.engine)
        val targetFile = File(targetDir, voiceInfo.modelFileName)
        val tempFile = File(targetDir, "${voiceInfo.modelFileName}.downloading")

        AppLogger.i(TAG, "Starting download for ${voiceInfo.name} (${voiceInfo.formattedSize}) from ${voiceInfo.onnxDownloadUrl}")

        try {
            // 1. Download model file if not already present
            if (!targetFile.exists() || targetFile.length() < 1024L) {
                downloadFileWithRedirects(
                    sourceUrl = voiceInfo.onnxDownloadUrl,
                    destinationFile = tempFile,
                    expectedSize = voiceInfo.sizeBytes,
                    onProgress = onProgress
                )

                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                AppLogger.i(TAG, "Successfully downloaded model file: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            } else {
                AppLogger.i(TAG, "Model file already exists: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            }

            // 2. Download voices.bin if specified
            voiceInfo.voicesDownloadUrl?.let { voicesUrl ->
                val voicesFile = File(targetDir, "voices.bin")
                if (!voicesFile.exists() || voicesFile.length() < 100L) {
                    val voicesTemp = File(targetDir, "voices.bin.downloading")
                    try {
                        AppLogger.i(TAG, "Downloading voices.bin from $voicesUrl")
                        downloadFileWithRedirects(
                            sourceUrl = voicesUrl,
                            destinationFile = voicesTemp,
                            expectedSize = -1L,
                            onProgress = { _, _ -> }
                        )
                        if (!voicesTemp.renameTo(voicesFile)) {
                            voicesTemp.copyTo(voicesFile, overwrite = true)
                            voicesTemp.delete()
                        }
                        AppLogger.i(TAG, "Successfully downloaded voices.bin (${voicesFile.length()} bytes)")
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Failed to download voices.bin: ${e.message}", e)
                        if (voicesTemp.exists()) voicesTemp.delete()
                        throw e
                    }
                }
            }

            // 3. Download tokens.txt if specified
            val tokensUrlToUse = voiceInfo.tokensDownloadUrl ?: voiceInfo.configDownloadUrl?.takeIf { it.endsWith("tokens.txt") }
            tokensUrlToUse?.let { tokensUrl ->
                val tokensFile = File(targetDir, "tokens.txt")
                if (!tokensFile.exists() || tokensFile.length() < 10L) {
                    val tokensTemp = File(targetDir, "tokens.txt.downloading")
                    try {
                        AppLogger.i(TAG, "Downloading tokens.txt from $tokensUrl")
                        downloadFileWithRedirects(
                            sourceUrl = tokensUrl,
                            destinationFile = tokensTemp,
                            expectedSize = -1L,
                            onProgress = { _, _ -> }
                        )
                        if (!tokensTemp.renameTo(tokensFile)) {
                            tokensTemp.copyTo(tokensFile, overwrite = true)
                            tokensTemp.delete()
                        }
                        AppLogger.i(TAG, "Successfully downloaded tokens.txt (${tokensFile.length()} bytes)")
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Failed to download tokens.txt: ${e.message}", e)
                        if (tokensTemp.exists()) tokensTemp.delete()
                        throw e
                    }
                }
            }

            // 4. Download lexicon if specified
            voiceInfo.lexiconDownloadUrl?.let { lexiconUrl ->
                val lexiconFileName = lexiconUrl.substringAfterLast("/", "lexicon.txt")
                val lexiconFile = File(targetDir, lexiconFileName)
                if (!lexiconFile.exists() || lexiconFile.length() < 1024L) {
                    val lexiconTemp = File(targetDir, "$lexiconFileName.downloading")
                    try {
                        AppLogger.i(TAG, "Downloading $lexiconFileName from $lexiconUrl")
                        downloadFileWithRedirects(
                            sourceUrl = lexiconUrl,
                            destinationFile = lexiconTemp,
                            expectedSize = -1L,
                            onProgress = { _, _ -> }
                        )
                        if (!lexiconTemp.renameTo(lexiconFile)) {
                            lexiconTemp.copyTo(lexiconFile, overwrite = true)
                            lexiconTemp.delete()
                        }
                        AppLogger.i(TAG, "Successfully downloaded $lexiconFileName (${lexiconFile.length()} bytes)")
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Failed to download lexicon: ${e.message}", e)
                        if (lexiconTemp.exists()) lexiconTemp.delete()
                    }
                }
            }

            // 5. Download accompanying JSON config if available and not tokens.txt
            voiceInfo.configDownloadUrl?.takeIf { !it.endsWith("tokens.txt") }?.let { configUrl ->
                val configFile = File(targetDir, "${voiceInfo.modelFileName}.json")
                if (!configFile.exists()) {
                    val configTemp = File(targetDir, "${voiceInfo.modelFileName}.json.downloading")
                    try {
                        downloadFileWithRedirects(
                            sourceUrl = configUrl,
                            destinationFile = configTemp,
                            expectedSize = -1L,
                            onProgress = { _, _ -> }
                        )
                        if (!configTemp.renameTo(configFile)) {
                            configTemp.copyTo(configFile, overwrite = true)
                            configTemp.delete()
                        }
                        AppLogger.i(TAG, "Successfully downloaded config file for ${voiceInfo.name}")
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Non-critical: failed to download model config: ${e.message}", e)
                        if (configTemp.exists()) configTemp.delete()
                    }
                }
            }

            // 6. Ensure support assets (espeak-ng-data) are available for all engines
            ensurePiperSupportAssets(context, targetDir)

            Result.success(targetFile)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to download model for ${voiceInfo.name}: ${e.message}", e)
            if (tempFile.exists()) {
                tempFile.delete()
            }
            Result.failure(e)
        }
    }

    private suspend fun downloadFileWithRedirects(
        sourceUrl: String,
        destinationFile: File,
        expectedSize: Long,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ) {
        var currentUrl = sourceUrl
        var redirectCount = 0
        var connection: HttpURLConnection

        while (true) {
            val url = URL(currentUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 60000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "BookReaderPro-Android")
            }

            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 || code == 308
            ) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location != null && redirectCount < MAX_REDIRECTS) {
                    currentUrl = location
                    redirectCount++
                    continue
                }
            }
            break
        }

        if (connection.responseCode !in 200..299) {
            val errorMsg = "HTTP error ${connection.responseCode}: ${connection.responseMessage}"
            connection.disconnect()
            throw IOException(errorMsg)
        }

        val totalBytes = if (connection.contentLengthLong > 0) connection.contentLengthLong else expectedSize
        var bytesReadSoFar = 0L
        val buffer = ByteArray(BUFFER_SIZE)

        try {
            connection.inputStream.use { input ->
                destinationFile.outputStream().use { output ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        coroutineContext.ensureActive()
                        output.write(buffer, 0, read)
                        bytesReadSoFar += read
                        onProgress(bytesReadSoFar, totalBytes)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    fun ensurePiperSupportAssets(context: Context, targetDir: File) {
        val tokensFile = File(targetDir, "tokens.txt")
        if (!tokensFile.exists() || tokensFile.length() == 0L) {
            try {
                context.assets.open("vits-piper-en_US-lessac-medium/tokens.txt").use { input ->
                    tokensFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                AppLogger.i(TAG, "Copied tokens.txt to ${tokensFile.absolutePath}")
            } catch (e: Exception) {
                AppLogger.d(TAG, "tokens.txt not found in assets: ${e.message}")
            }
        }

        val espeakDir = File(targetDir, "espeak-ng-data")
        val defaultEspeakDir = File(context.filesDir, "vits-piper-en_US-lessac-medium/espeak-ng-data")
        if (!espeakDir.exists()) {
            if (defaultEspeakDir.exists()) {
                try {
                    defaultEspeakDir.copyRecursively(espeakDir, overwrite = false)
                    AppLogger.i(TAG, "Copied espeak-ng-data to ${espeakDir.absolutePath}")
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Could not copy espeak-ng-data from filesDir: ${e.message}", e)
                }
            } else {
                try {
                    copyAssetDirRecursively(context, "vits-piper-en_US-lessac-medium/espeak-ng-data", espeakDir)
                    AppLogger.i(TAG, "Extracted espeak-ng-data from assets to ${espeakDir.absolutePath}")
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Could not extract espeak-ng-data from assets: ${e.message}", e)
                }
            }
        }
    }

    private fun copyAssetDirRecursively(context: Context, assetPath: String, destDir: File) {
        val assetManager = context.assets
        val children = try {
            assetManager.list(assetPath)
        } catch (_: Exception) {
            null
        }

        if (!children.isNullOrEmpty()) {
            if (!destDir.exists() && !destDir.mkdirs()) {
                throw IOException("Failed to create dir: ${destDir.absolutePath}")
            }
            for (child in children) {
                copyAssetDirRecursively(context, "$assetPath/$child", File(destDir, child))
            }
        } else {
            destDir.parentFile?.mkdirs()
            try {
                assetManager.open(assetPath).use { input ->
                    destDir.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                AppLogger.d(TAG, "Skipped asset leaf: $assetPath (${e.message})")
            }
        }
    }
}

