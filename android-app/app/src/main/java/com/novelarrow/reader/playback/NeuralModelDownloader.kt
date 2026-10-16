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
        val dir = File(context.filesDir, "neural_tts/${engine.lowercase()}")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getModelFile(context: Context, voiceInfo: NeuralVoiceInfo): File {
        return File(getModelDirectory(context, voiceInfo.engine), voiceInfo.modelFileName)
    }

    fun isModelDownloaded(context: Context, voiceInfo: NeuralVoiceInfo): Boolean {
        val file = getModelFile(context, voiceInfo)
        return file.exists() && file.length() > 1024L
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
            downloadFileWithRedirects(
                sourceUrl = voiceInfo.onnxDownloadUrl,
                destinationFile = tempFile,
                expectedSize = voiceInfo.sizeBytes,
                onProgress = onProgress
            )

            if (!tempFile.renameTo(targetFile)) {
                // Fallback copy if atomic rename fails
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            AppLogger.i(TAG, "Successfully downloaded model file: ${targetFile.absolutePath} (${targetFile.length()} bytes)")

            // Download accompanying JSON config if available
            voiceInfo.configDownloadUrl?.let { configUrl ->
                val configFile = File(targetDir, "${voiceInfo.modelFileName}.json")
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
                setRequestProperty("User-Agent", "NovelArrowReaderPro-Android")
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
}
