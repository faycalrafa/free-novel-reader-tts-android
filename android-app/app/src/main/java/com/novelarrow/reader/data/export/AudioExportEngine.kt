package com.novelarrow.reader.data.export

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.data.model.ReaderSettings
import com.novelarrow.reader.data.parser.ChapterParser
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.util.AppLogger
import de.sciss.jump3r.Main
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID

/**
 * High-performance audio synthesis and MP3 conversion engine.
 * Synthesizes chapter paragraphs respecting all TTS rules (header/footer skip,
 * content selector, pronunciation, skip words), concatenates PCM audio streams,
 * and encodes to MP3 via pure-Java LAME.
 */
typealias GranularProgressCallback = (
    currentChapter: Int,
    totalChapters: Int,
    currentParagraph: Int,
    totalParagraphs: Int,
    percent: Int,
    statusText: String
) -> Unit

object AudioExportEngine {

    private const val TAG = "AudioExportEngine"
    private const val ONE_MB = 1024L * 1024L
    private const val MAX_TTS_CHUNK_LENGTH = 3500

    fun calculateGranularProgress(
        currentChapter: Int,
        totalChapters: Int,
        currentParagraph: Int,
        totalParagraphs: Int
    ): Int {
        if (totalChapters <= 0 || currentChapter <= 0) return 0
        val safeChapter = currentChapter.coerceIn(1, totalChapters)
        val safeParaFraction = if (totalParagraphs > 0) {
            currentParagraph.coerceIn(0, totalParagraphs).toDouble() / totalParagraphs.toDouble()
        } else 0.0
        val completedUnits = (safeChapter - 1).toDouble() + safeParaFraction
        val progress = (completedUnits / totalChapters.toDouble()) * 100.0
        return (progress + 1e-9).toInt().coerceIn(0, 100)
    }

    enum class SplitMode {
        BY_CHAPTER,
        BY_MINUTES,
        SINGLE_FILE
    }

    data class ExportRequest(
        val novel: Novel,
        val chapters: List<Chapter>,
        val splitMode: SplitMode,
        val maxMinutesPerFile: Int = 30,
        val speechRate: Float = 1.0f,
        val speechPitch: Float = 1.0f,
        val voiceName: String? = null,
        val customTitle: String? = null
    )

    data class ExportResult(
        val outputFiles: List<File>,
        val playlistFile: File? = null,
        val totalDurationSeconds: Long = 0,
        val isCancelled: Boolean = false
    )

    suspend fun exportToMp3(
        context: Context,
        request: ExportRequest,
        libraryRepository: LibraryRepository,
        settingsRepository: SettingsRepository,
        onProgress: GranularProgressCallback? = null,
        isCancelled: () -> Boolean = { false }
    ): ExportResult = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "Starting MP3 export for novel '${request.novel.title}' with ${request.chapters.size} chapters. SplitMode: ${request.splitMode}, Speed: ${request.speechRate}x")

        val settings = settingsRepository.getSettings()
        val tempDir = File(context.cacheDir, "audio_export_${UUID.randomUUID()}").apply { mkdirs() }
        val finalOutputFiles = mutableListOf<File>()

        var ttsEngine: TextToSpeech? = null
        val initDeferred = CompletableDeferred<Boolean>()

        withContext(Dispatchers.Main) {
            ttsEngine = TextToSpeech(context.applicationContext) { status ->
                initDeferred.complete(status == TextToSpeech.SUCCESS)
            }
        }

        val initialized = initDeferred.await()
        if (!initialized || ttsEngine == null) {
            AppLogger.e(TAG, "Failed to initialize TextToSpeech for MP3 export")
            tempDir.deleteRecursively()
            return@withContext ExportResult(emptyList(), isCancelled = true)
        }

        val tts = ttsEngine
        try {
            // Apply speed and pitch
            tts.setSpeechRate(request.speechRate)
            tts.setPitch(request.speechPitch)

            // Apply voice if specified
            if (!request.voiceName.isNullOrBlank()) {
                val matchedVoice = tts.voices?.firstOrNull { it.name == request.voiceName }
                if (matchedVoice != null) {
                    tts.voice = matchedVoice
                }
            }

            val totalChapters = request.chapters.size
            val safeNovelTitle = sanitizeFilename(request.customTitle ?: request.novel.title)

            when (request.splitMode) {
                SplitMode.BY_CHAPTER -> {
                    // Export chapter by chapter
                    request.chapters.forEachIndexed { index, chapter ->
                        if (isCancelled()) {
                            AppLogger.i(TAG, "Export cancelled by user at chapter ${index + 1}")
                            return@forEachIndexed
                        }

                        val initialPct = calculateGranularProgress(index + 1, totalChapters, 0, 0)
                        onProgress?.invoke(index + 1, totalChapters, 0, 0, initialPct, "Chapter ${index + 1}/$totalChapters: ${chapter.title}")

                        val chapterWav = synthesizeChapterToWav(
                            context = context,
                            tts = tts,
                            chapter = chapter,
                            libraryRepository = libraryRepository,
                            settings = settings,
                            tempDir = tempDir,
                            isCancelled = isCancelled,
                            onParagraphProgress = { curPara, totalParas ->
                                val pct = calculateGranularProgress(index + 1, totalChapters, curPara, totalParas)
                                val status = "Chapter ${index + 1}/$totalChapters • ¶ $curPara/$totalParas: ${chapter.title}"
                                onProgress?.invoke(index + 1, totalChapters, curPara, totalParas, pct, status)
                            }
                        )

                        if (chapterWav != null && chapterWav.exists() && chapterWav.length() > 44) {
                            val mp3Name = String.format("%s_Ch%03d_%s.mp3", safeNovelTitle, index + 1, sanitizeFilename(chapter.title))
                            val mp3File = File(tempDir, mp3Name)
                            encodeWavToMp3(chapterWav, mp3File)
                            chapterWav.delete()

                            if (mp3File.exists()) {
                                finalOutputFiles.add(saveToDownloadsDirectory(context, mp3File, safeNovelTitle))
                            }
                        }
                    }

                    // Create M3U playlist file
                    val playlistFile = if (finalOutputFiles.isNotEmpty()) {
                        createM3uPlaylist(context, safeNovelTitle, finalOutputFiles, safeNovelTitle)
                    } else null

                    ExportResult(finalOutputFiles, playlistFile, isCancelled = isCancelled())
                }

                SplitMode.SINGLE_FILE -> {
                    // Collect all chapters into a single WAV and encode
                    val chapterWavs = mutableListOf<File>()
                    request.chapters.forEachIndexed { index, chapter ->
                        if (isCancelled()) return@forEachIndexed
                        val rawInitialPct = calculateGranularProgress(index + 1, totalChapters, 0, 0)
                        val initialPct = ((rawInitialPct * 85) / 100).coerceIn(0, 85)
                        onProgress?.invoke(index + 1, totalChapters, 0, 0, initialPct, "Synthesizing Chapter ${index + 1}/$totalChapters: ${chapter.title}")

                        val chWav = synthesizeChapterToWav(
                            context = context,
                            tts = tts,
                            chapter = chapter,
                            libraryRepository = libraryRepository,
                            settings = settings,
                            tempDir = tempDir,
                            isCancelled = isCancelled,
                            onParagraphProgress = { curPara, totalParas ->
                                val rawPct = calculateGranularProgress(index + 1, totalChapters, curPara, totalParas)
                                val pct = ((rawPct * 85) / 100).coerceIn(0, 85)
                                val status = "Synthesizing Chapter ${index + 1}/$totalChapters • ¶ $curPara/$totalParas: ${chapter.title}"
                                onProgress?.invoke(index + 1, totalChapters, curPara, totalParas, pct, status)
                            }
                        )
                        if (chWav != null && chWav.exists() && chWav.length() > 44) {
                            chapterWavs.add(chWav)
                        }
                    }

                    if (isCancelled() || chapterWavs.isEmpty()) {
                        return@withContext ExportResult(emptyList(), isCancelled = true)
                    }

                    onProgress?.invoke(totalChapters, totalChapters, 0, 0, 90, "Merging audio into single file...")
                    val combinedWav = File(tempDir, "${safeNovelTitle}_combined.wav")
                    concatenateWavFiles(chapterWavs, combinedWav)
                    chapterWavs.forEach { it.delete() }

                    onProgress?.invoke(totalChapters, totalChapters, 0, 0, 95, "Encoding MP3 audio...")
                    val finalMp3 = File(tempDir, "${safeNovelTitle}.mp3")
                    encodeWavToMp3(combinedWav, finalMp3)
                    combinedWav.delete()

                    val exportedFile = saveToDownloadsDirectory(context, finalMp3, safeNovelTitle)
                    onProgress?.invoke(totalChapters, totalChapters, 0, 0, 100, "Export complete")
                    ExportResult(listOf(exportedFile), isCancelled = isCancelled())
                }

                SplitMode.BY_MINUTES -> {
                    val targetMinutes = request.maxMinutesPerFile.coerceAtLeast(1)
                    var currentPartWavs = mutableListOf<File>()
                    var currentPartDurationSeconds = 0.0
                    val maxPartSeconds = targetMinutes * 60.0
                    var partNumber = 1

                    request.chapters.forEachIndexed { index, chapter ->
                        if (isCancelled()) return@forEachIndexed
                        val initialPct = calculateGranularProgress(index + 1, totalChapters, 0, 0)
                        onProgress?.invoke(index + 1, totalChapters, 0, 0, initialPct, "Chapter ${index + 1}/$totalChapters: ${chapter.title}")

                        val chWav = synthesizeChapterToWav(
                            context = context,
                            tts = tts,
                            chapter = chapter,
                            libraryRepository = libraryRepository,
                            settings = settings,
                            tempDir = tempDir,
                            isCancelled = isCancelled,
                            onParagraphProgress = { curPara, totalParas ->
                                val pct = calculateGranularProgress(index + 1, totalChapters, curPara, totalParas)
                                val status = "Chapter ${index + 1}/$totalChapters • ¶ $curPara/$totalParas: ${chapter.title}"
                                onProgress?.invoke(index + 1, totalChapters, curPara, totalParas, pct, status)
                            }
                        )

                        if (chWav != null && chWav.exists() && chWav.length() > 44) {
                            val durationSec = getWavDurationSeconds(chWav)
                            if (currentPartDurationSeconds + durationSec > maxPartSeconds && currentPartWavs.isNotEmpty()) {
                                // Finalize current part
                                val partWav = File(tempDir, "${safeNovelTitle}_part${partNumber}.wav")
                                concatenateWavFiles(currentPartWavs, partWav)
                                currentPartWavs.forEach { it.delete() }
                                currentPartWavs.clear()

                                val partMp3 = File(tempDir, String.format("%s_Part%02d.mp3", safeNovelTitle, partNumber))
                                encodeWavToMp3(partWav, partMp3)
                                partWav.delete()

                                if (partMp3.exists()) {
                                    finalOutputFiles.add(saveToDownloadsDirectory(context, partMp3, safeNovelTitle))
                                }
                                partNumber++
                                currentPartDurationSeconds = 0.0
                            }

                            currentPartWavs.add(chWav)
                            currentPartDurationSeconds += durationSec
                        }
                    }

                    // Remaining part
                    if (currentPartWavs.isNotEmpty() && !isCancelled()) {
                        val partWav = File(tempDir, "${safeNovelTitle}_part${partNumber}.wav")
                        concatenateWavFiles(currentPartWavs, partWav)
                        currentPartWavs.forEach { it.delete() }

                        val partMp3 = File(tempDir, String.format("%s_Part%02d.mp3", safeNovelTitle, partNumber))
                        encodeWavToMp3(partWav, partMp3)
                        partWav.delete()

                        if (partMp3.exists()) {
                            finalOutputFiles.add(saveToDownloadsDirectory(context, partMp3, safeNovelTitle))
                        }
                    }

                    val playlistFile = if (finalOutputFiles.isNotEmpty()) {
                        createM3uPlaylist(context, safeNovelTitle, finalOutputFiles, safeNovelTitle)
                    } else null

                    ExportResult(finalOutputFiles, playlistFile, isCancelled = isCancelled())
                }
            }
        } finally {
            withContext(Dispatchers.Main) {
                try {
                    tts.stop()
                    tts.shutdown()
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Error shutting down export TTS: ${e.message}")
                }
            }
            tempDir.deleteRecursively()
        }
    }

    suspend fun synthesizeTextToMp3(
        context: Context,
        title: String,
        text: String,
        speechRate: Float = 1.0f,
        speechPitch: Float = 1.0f,
        voiceName: String? = null,
        onProgress: ((percent: Int, statusText: String) -> Unit)? = null
    ): File? = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "audio_export_${UUID.randomUUID()}").apply { mkdirs() }
        val initDeferred = CompletableDeferred<Boolean>()
        var ttsInstance: TextToSpeech? = null

        withContext(Dispatchers.Main) {
            ttsInstance = TextToSpeech(context.applicationContext) { status ->
                initDeferred.complete(status == TextToSpeech.SUCCESS)
            }
        }

        val initialized = initDeferred.await()
        if (!initialized || ttsInstance == null) {
            tempDir.deleteRecursively()
            return@withContext null
        }

        val tts = ttsInstance
        try {
            tts.setSpeechRate(speechRate)
            tts.setPitch(speechPitch)
            if (!voiceName.isNullOrBlank()) {
                tts.voices?.firstOrNull { it.name == voiceName }?.let { tts.voice = it }
            }

            val chunks = splitTextIntoSafeChunks(text)
            val chunkWavs = mutableListOf<File>()

            chunks.forEachIndexed { idx, chunk ->
                val pct = ((idx.toFloat() / chunks.size.toFloat()) * 80).toInt()
                onProgress?.invoke(pct, "Synthesizing audio: Chunk ${idx + 1}/${chunks.size}")

                val wav = File(tempDir, "chunk_$idx.wav")
                val success = synthesizeChunkToFile(tts, chunk, wav)
                if (success && wav.exists() && wav.length() > 44) {
                    chunkWavs.add(wav)
                }
            }

            if (chunkWavs.isEmpty()) return@withContext null

            onProgress?.invoke(85, "Combining audio...")
            val combinedWav = File(tempDir, "combined.wav")
            concatenateWavFiles(chunkWavs, combinedWav)
            chunkWavs.forEach { it.delete() }

            onProgress?.invoke(90, "Encoding MP3...")
            val safeTitle = sanitizeFilename(title)
            val mp3File = File(tempDir, "${safeTitle}.mp3")
            encodeWavToMp3(combinedWav, mp3File)
            combinedWav.delete()

            saveToDownloadsDirectory(context, mp3File, subFolder = null)
        } finally {
            withContext(Dispatchers.Main) {
                try {
                    tts.stop()
                    tts.shutdown()
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Error shutting down text synthesis TTS: ${e.message}", e)
                }
            }
            tempDir.deleteRecursively()
        }
    }

    private suspend fun synthesizeChapterToWav(
        context: Context,
        tts: TextToSpeech,
        chapter: Chapter,
        libraryRepository: LibraryRepository,
        settings: ReaderSettings,
        tempDir: File,
        isCancelled: () -> Boolean,
        onParagraphProgress: ((currentPara: Int, totalParas: Int) -> Unit)? = null
    ): File? {
        val rawContent = try {
            libraryRepository.readChapterContent(chapter)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error reading chapter content for '${chapter.title}': ${e.message}")
            return null
        }
        if (rawContent.isBlank()) return null

        // Parse paragraphs strictly obeying all user TTS rules
        val parsed = ChapterParser.parse(
            content = rawContent,
            fileName = chapter.title,
            contentSelector = settings.contentSelector,
            contentSelectorEnabled = settings.contentSelectorEnabled,
            pronunciationRules = settings.pronunciationRules,
            pronunciationRulesEnabled = settings.pronunciationRulesEnabled,
            skipWords = settings.skipWords,
            skipWordsEnabled = settings.skipWordsEnabled,
            headerSkipString = settings.headerSkipString,
            headerSkipEnabled = settings.headerSkipEnabled,
            footerSkipString = settings.footerSkipString,
            footerSkipEnabled = settings.footerSkipEnabled,
            headerSkipRules = settings.headerSkipRules,
            footerSkipRules = settings.footerSkipRules,
            folderPath = chapter.fileUri,
            novelUrl = chapter.webSourceUrl
        )
        if (parsed.isEmpty()) return null

        val chunkWavs = mutableListOf<File>()
        var chunkIndex = 0
        val totalParas = parsed.size

        for ((pIdx, p) in parsed.withIndex()) {
            if (isCancelled()) break
            onParagraphProgress?.invoke(pIdx + 1, totalParas)
            val textToSpeak = p.spokenText.ifBlank { p.rawText }
            if (textToSpeak.isBlank()) continue

            val chunks = splitTextIntoSafeChunks(textToSpeak)
            for (chunk in chunks) {
                if (isCancelled()) break
                val chunkFile = File(tempDir, "chap_${chapter.id}_chunk_${chunkIndex++}.wav")
                val success = synthesizeChunkToFile(tts, chunk, chunkFile)
                if (success && chunkFile.exists() && chunkFile.length() > 44) {
                    chunkWavs.add(chunkFile)
                }
            }
        }

        if (chunkWavs.isEmpty()) return null

        val chapterWav = File(tempDir, "chapter_${sanitizeFilename(chapter.title)}.wav")
        concatenateWavFiles(chunkWavs, chapterWav)
        chunkWavs.forEach { it.delete() }
        return chapterWav
    }

    private suspend fun synthesizeChunkToFile(tts: TextToSpeech, text: String, file: File): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        val utteranceId = UUID.randomUUID().toString()

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) deferred.complete(true)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) {
                    AppLogger.w(TAG, "TTS synthesizeToFile error for utterance $id")
                    deferred.complete(false)
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId) {
                    AppLogger.w(TAG, "TTS synthesizeToFile errorCode: $errorCode")
                    deferred.complete(false)
                }
            }
        })

        val params = Bundle()
        val result = tts.synthesizeToFile(text, params, file, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            AppLogger.w(TAG, "tts.synthesizeToFile returned code: $result")
            return false
        }

        return deferred.await()
    }

    private fun encodeWavToMp3(wavFile: File, mp3File: File): Boolean {
        return try {
            val main = Main()
            val args = arrayOf("-b", "128", wavFile.absolutePath, mp3File.absolutePath)
            main.run(args)
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed encoding WAV to MP3: ${e.message}", e)
            false
        }
    }

    fun concatenateWavFiles(wavFiles: List<File>, outputFile: File) {
        if (wavFiles.isEmpty()) return
        if (wavFiles.size == 1) {
            wavFiles[0].copyTo(outputFile, overwrite = true)
            return
        }

        val firstFile = wavFiles[0]
        val headerBytes = ByteArray(44)
        FileInputStream(firstFile).use { it.read(headerBytes) }

        var totalPcmBytes = 0L
        FileOutputStream(outputFile).use { fos ->
            fos.write(headerBytes) // Placeholder header
            val buffer = ByteArray(16 * 1024)

            for (file in wavFiles) {
                FileInputStream(file).use { fis ->
                    fis.skip(44) // Skip WAV header
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                        totalPcmBytes += bytesRead
                    }
                }
            }
        }

        // Correct WAV header chunk sizes in outputFile
        RandomAccessFile(outputFile, "rw").use { raf ->
            val totalFileSize = 44 + totalPcmBytes
            // Offset 4: ChunkSize (FileSize - 8)
            val chunkSize = (totalFileSize - 8).toInt()
            val chunkSizeBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunkSize).array()
            raf.seek(4)
            raf.write(chunkSizeBytes)

            // Offset 40: Subchunk2Size (total PCM bytes)
            val subchunk2Bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalPcmBytes.toInt()).array()
            raf.seek(40)
            raf.write(subchunk2Bytes)
        }
    }

    private fun getWavDurationSeconds(wavFile: File): Double {
        return try {
            val header = ByteArray(44)
            FileInputStream(wavFile).use { it.read(header) }
            val byteRate = ByteBuffer.wrap(header, 28, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val pcmBytes = (wavFile.length() - 44).coerceAtLeast(0)
            if (byteRate > 0) pcmBytes.toDouble() / byteRate.toDouble() else 0.0
        } catch (_: Exception) {
            0.0
        }
    }

    private fun createM3uPlaylist(context: Context, novelTitle: String, mp3Files: List<File>, subFolder: String? = null): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val baseDir = File(downloadsDir, "BookReader")
        val targetDir = if (!subFolder.isNullOrBlank()) {
            File(baseDir, sanitizeFilename(subFolder)).apply { mkdirs() }
        } else {
            baseDir.apply { mkdirs() }
        }
        val m3uFile = File(targetDir, "${sanitizeFilename(novelTitle)}_playlist.m3u")
        m3uFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("#EXTM3U\n")
            mp3Files.forEach { file ->
                writer.write("#EXTINF:-1,${file.nameWithoutExtension}\n")
                writer.write("${file.name}\n")
            }
        }
        MediaScannerConnection.scanFile(context, arrayOf(m3uFile.absolutePath), null, null)
        return m3uFile
    }

    fun saveToDownloadsDirectory(context: Context, file: File, subFolder: String? = null): File {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val baseDir = File(downloadsDir, "BookReader")
            val targetDir = if (!subFolder.isNullOrBlank()) {
                File(baseDir, sanitizeFilename(subFolder)).apply { mkdirs() }
            } else {
                baseDir.apply { mkdirs() }
            }
            val destFile = File(targetDir, file.name)
            file.copyTo(destFile, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
            AppLogger.i(TAG, "Saved MP3 file to public Downloads: ${destFile.absolutePath}")
            destFile
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed saving to public Downloads: ${e.message}", e)
            file
        }
    }

    fun splitTextIntoSafeChunks(text: String): List<String> {
        if (text.length <= MAX_TTS_CHUNK_LENGTH) return listOf(text)

        val chunks = mutableListOf<String>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            if (remaining.length <= MAX_TTS_CHUNK_LENGTH) {
                chunks.add(remaining)
                break
            }

            val window = remaining.substring(0, MAX_TTS_CHUNK_LENGTH)
            // Try to split at sentence delimiters or punctuation
            val breakIdx = window.lastIndexOfAny(charArrayOf('.', '!', '?', '\n', '。', '！', '？', ';', '；'))
            val splitAt = if (breakIdx > MAX_TTS_CHUNK_LENGTH / 2) {
                breakIdx + 1
            } else {
                val spaceIdx = window.lastIndexOf(' ')
                if (spaceIdx > MAX_TTS_CHUNK_LENGTH / 2) spaceIdx + 1 else MAX_TTS_CHUNK_LENGTH
            }

            chunks.add(remaining.substring(0, splitAt).trim())
            remaining = remaining.substring(splitAt).trim()
        }

        return chunks
    }

    fun sanitizeFilename(name: String): String {
        val cleaned = name.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_").trim()
        val meaningful = cleaned.filter { it.isLetterOrDigit() }
        return if (meaningful.isBlank()) "bookreader_audio" else cleaned
    }
}
