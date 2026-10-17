package com.novelarrow.reader.playback.local

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thread-safe wrapper for offline on-device Text-to-Speech powered by sherpa-onnx (k2-fsa).
 * Manages model extraction, native JNI pointers, dynamic voice loading, and AudioTrack PCM float streaming.
 */
class LocalTtsEngine(
    context: Context,
    private val modelDirName: String = "vits-piper-en_US-lessac-medium",
    private val modelFileName: String = "en_US-lessac-medium.onnx",
    private val numThreads: Int = 2,
    private val defaultSpeakerId: Int = 0
) : AutoCloseable {

    companion object {
        private const val TAG = "LocalTtsEngine"
    }

    private val appContext = context.applicationContext
    private val engineMutex = Mutex()
    private val isReleased = AtomicBoolean(false)
    private val isSpeaking = AtomicBoolean(false)

    private var offlineTts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private var currentTrackSampleRate: Int = 0
    private var activeModelPath: String? = null

    val isInitialized: Boolean
        get() = offlineTts != null && !isReleased.get()

    /**
     * Checks whether the required model, tokens.txt, and espeak-ng-data directory exist.
     */
    fun isModelReady(customModelFile: File? = null): Boolean {
        val targetDir = File(appContext.filesDir, modelDirName)
        val model = customModelFile ?: File(targetDir, modelFileName)
        val tokens = File(targetDir, "tokens.txt")
        val dataDir = File(targetDir, "espeak-ng-data")

        return model.exists() && model.length() > 1024L &&
                tokens.exists() && tokens.length() > 0L &&
                dataDir.exists() && dataDir.isDirectory
    }

    /**
     * Initializes or reloads the engine with an optional custom model file, tokens file, and data dir.
     * Extracts support assets (tokens.txt, espeak-ng-data) from APK assets if not yet present in filesDir.
     */
    suspend fun initialize(
        modelFile: File? = null,
        tokensFile: File? = null,
        dataDir: File? = null,
        engineType: String = "PIPER"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            if (isReleased.get()) {
                val err = IllegalStateException("LocalTtsEngine has already been released.")
                AppLogger.e(TAG, err.message ?: "Released", err)
                return@withContext Result.failure(err)
            }

            val targetDir = File(appContext.filesDir, modelDirName)
            val effectiveModelFile = modelFile ?: File(targetDir, modelFileName)
            val effectiveModelPath = effectiveModelFile.absolutePath

            // If already initialized with this exact model file, reuse it
            if (offlineTts != null && activeModelPath == effectiveModelPath) {
                return@withContext Result.success(Unit)
            }

            try {
                // 1. Ensure support assets (tokens.txt, espeak-ng-data) are extracted to targetDir
                val targetTokens = tokensFile ?: File(targetDir, "tokens.txt")
                val targetDataDir = dataDir ?: File(targetDir, "espeak-ng-data")

                if (!targetTokens.exists() || !targetDataDir.exists() || !File(targetDataDir, "phontab").exists()) {
                    AppLogger.i(TAG, "Extracting TTS support assets from assets/$modelDirName to ${targetDir.absolutePath}...")
                    copyAssetsRecursively(modelDirName, targetDir)
                }

                // If default model file is missing from disk, try copying it from assets too
                if (!effectiveModelFile.exists()) {
                    AppLogger.i(TAG, "Copying model file from assets: $modelFileName")
                    copyAssetsRecursively(modelDirName, targetDir)
                }

                if (!effectiveModelFile.exists() || effectiveModelFile.length() < 1024L) {
                    val ex = IOException("Model file not found or corrupted: ${effectiveModelFile.absolutePath}")
                    AppLogger.w(TAG, ex.message ?: "Model missing")
                    return@withContext Result.failure(ex)
                }

                val tokensPathToUse = if (targetTokens.exists()) targetTokens.absolutePath else File(targetDir, "tokens.txt").absolutePath
                val dataDirPathToUse = if (targetDataDir.exists()) targetDataDir.absolutePath else File(targetDir, "espeak-ng-data").absolutePath

                // Pre-flight check: espeak-ng-data must have phontab or native C++ will abort()
                val phontab = File(dataDirPathToUse, "phontab")
                if (!phontab.exists()) {
                    copyAssetsRecursively(modelDirName, targetDir)
                }

                val modelConfig = when (engineType.uppercase()) {
                    "KITTEN" -> {
                        val voicesFile = File(effectiveModelFile.parentFile, "voices.bin")
                        val kittenConfig = OfflineTtsKittenModelConfig(
                            model = effectiveModelPath,
                            voices = if (voicesFile.exists()) voicesFile.absolutePath else "",
                            tokens = tokensPathToUse,
                            dataDir = dataDirPathToUse,
                            lengthScale = 1.0f
                        )
                        OfflineTtsModelConfig(
                            kitten = kittenConfig,
                            numThreads = numThreads,
                            debug = false,
                            provider = "cpu"
                        )
                    }
                    "KOKORO" -> {
                        val voicesFile = File(effectiveModelFile.parentFile, "voices.bin")
                        val lexiconFile = File(effectiveModelFile.parentFile, "lexicon.txt")
                        val kokoroConfig = OfflineTtsKokoroModelConfig(
                            model = effectiveModelPath,
                            voices = if (voicesFile.exists()) voicesFile.absolutePath else "",
                            tokens = tokensPathToUse,
                            dataDir = dataDirPathToUse,
                            lexicon = if (lexiconFile.exists()) lexiconFile.absolutePath else "",
                            lang = "en-us",
                            dictDir = "",
                            lengthScale = 1.0f
                        )
                        OfflineTtsModelConfig(
                            kokoro = kokoroConfig,
                            numThreads = numThreads,
                            debug = false,
                            provider = "cpu"
                        )
                    }
                    else -> {
                        val vitsConfig = OfflineTtsVitsModelConfig(
                            model = effectiveModelPath,
                            lexicon = "",
                            tokens = tokensPathToUse,
                            dataDir = dataDirPathToUse,
                            dictDir = "",
                            noiseScale = 0.667f,
                            noiseScaleW = 0.8f,
                            lengthScale = 1.0f
                        )
                        OfflineTtsModelConfig(
                            vits = vitsConfig,
                            numThreads = numThreads,
                            debug = false,
                            provider = "cpu"
                        )
                    }
                }

                val config = OfflineTtsConfig(
                    model = modelConfig,
                    ruleFsts = "",
                    maxNumSentences = 1
                )

                // Clean up any existing native instance before loading new model
                offlineTts?.let {
                    try { it.release() } catch (e: Exception) { AppLogger.w(TAG, "Error releasing previous OfflineTts", e) }
                }

                val tts = OfflineTts(assetManager = null, config = config)
                offlineTts = tts
                activeModelPath = effectiveModelPath
                AppLogger.i(TAG, "sherpa-onnx OfflineTts initialized successfully! Model: ${effectiveModelFile.name}, Engine: $engineType, SampleRate: ${tts.sampleRate()} Hz, Speakers: ${tts.numSpeakers()}")
                Result.success(Unit)
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Fatal failure initializing sherpa-onnx OfflineTts: ${t.message}", t)
                Result.failure(t)
            }
        }
    }

    /**
     * Synthesizes text into raw IEEE Float PCM samples on Dispatchers.Default.
     */
    suspend fun generateAudio(
        text: String,
        speed: Float = 1.0f,
        speakerId: Int = defaultSpeakerId
    ): AudioData = withContext(Dispatchers.Default) {
        require(text.isNotBlank()) { "Input text cannot be blank." }

        if (offlineTts == null) {
            initialize().getOrThrow()
        }

        engineMutex.withLock {
            check(!isReleased.get()) { "Cannot generate audio: LocalTtsEngine is released." }
            val tts = checkNotNull(offlineTts) { "OfflineTts failed to initialize." }

            ensureActive()
            try {
                AppLogger.d(TAG, "Synthesizing text (length: ${text.length}, speed: $speed, sid: $speakerId)")
                val generated = tts.generate(
                    text = text.trim(),
                    sid = speakerId,
                    speed = speed
                )

                AudioData(
                    samples = generated.samples,
                    sampleRate = generated.sampleRate
                )
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Error generating audio for text: '${text.take(40)}...': ${t.message}", t)
                throw t
            }
        }
    }

    /**
     * Synthesizes and plays a paragraph, dispatching start, completion, and error events.
     * Integrates with AudioPlaybackCoordinator for paragraph-by-paragraph novel reading.
     */
    suspend fun speakParagraph(
        index: Int,
        text: String,
        speed: Float = 1.0f,
        speakerId: Int = defaultSpeakerId,
        onStart: (Int) -> Unit,
        onDone: (Int) -> Unit,
        onError: (Int, String) -> Unit
    ) = withContext(Dispatchers.Default) {
        stopPlayback()

        val audioData = try {
            generateAudio(text, speed, speakerId)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Audio generation failed for paragraph $index: ${t.message}", t)
            onError(index, t.message ?: "Synthesis failed")
            return@withContext
        }

        if (audioData.samples.isEmpty()) {
            onDone(index)
            return@withContext
        }

        isSpeaking.set(true)
        val sampleRate = audioData.sampleRate
        val samples = audioData.samples

        try {
            val track = getOrCreateAudioTrack(sampleRate)
            track.play()
            onStart(index)

            val chunkSize = 4096
            var offset = 0
            while (offset < samples.size && isSpeaking.get() && !isReleased.get()) {
                ensureActive()
                val length = minOf(chunkSize, samples.size - offset)
                val written = track.write(
                    samples,
                    offset,
                    length,
                    AudioTrack.WRITE_BLOCKING
                )
                if (written < 0) {
                    AppLogger.e(TAG, "AudioTrack write error: $written")
                    onError(index, "AudioTrack error: $written")
                    return@withContext
                }
                offset += written
            }

            // Wait for audio hardware buffer to finish playing
            if (isSpeaking.get() && !isReleased.get()) {
                val playbackDurationMs = audioData.durationMs
                val startTime = System.currentTimeMillis()
                while (isSpeaking.get() && (System.currentTimeMillis() - startTime) < playbackDurationMs) {
                    ensureActive()
                    delay(40)
                }
            }

            if (isSpeaking.get() && !isReleased.get()) {
                onDone(index)
            }
        } catch (e: CancellationException) {
            AppLogger.d(TAG, "speakParagraph canceled at index $index")
            throw e
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Playback failure in speakParagraph($index): ${t.message}", t)
            onError(index, t.message ?: "Playback failure")
        } finally {
            isSpeaking.set(false)
        }
    }

    /**
     * Synthesizes text and plays it immediately through an Android AudioTrack.
     */
    suspend fun speak(
        text: String,
        speed: Float = 1.0f,
        speakerId: Int = defaultSpeakerId
    ) = withContext(Dispatchers.Default) {
        val audioData = generateAudio(text, speed, speakerId)
        if (audioData.samples.isEmpty()) return@withContext

        isSpeaking.set(true)
        val sampleRate = audioData.sampleRate
        val samples = audioData.samples

        try {
            val track = getOrCreateAudioTrack(sampleRate)
            track.play()

            val chunkSize = 4096
            var offset = 0
            while (offset < samples.size && isSpeaking.get() && !isReleased.get()) {
                ensureActive()
                val length = minOf(chunkSize, samples.size - offset)
                val written = track.write(
                    samples,
                    offset,
                    length,
                    AudioTrack.WRITE_BLOCKING
                )
                if (written < 0) {
                    AppLogger.e(TAG, "AudioTrack write error code: $written")
                    break
                }
                offset += written
            }

            if (isSpeaking.get() && !isReleased.get()) {
                val playbackDurationMs = audioData.durationMs
                val startTime = System.currentTimeMillis()
                while (isSpeaking.get() && (System.currentTimeMillis() - startTime) < playbackDurationMs) {
                    ensureActive()
                    delay(40)
                }
            }
        } catch (e: CancellationException) {
            AppLogger.d(TAG, "AudioTrack playback canceled")
            throw e
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Playback failure in speak(): ${t.message}", t)
            throw t
        } finally {
            isSpeaking.set(false)
        }
    }

    /**
     * Immediately halts ongoing AudioTrack playback.
     */
    fun stopPlayback() {
        isSpeaking.set(false)
        try {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED &&
                    track.playState == AudioTrack.PLAYSTATE_PLAYING
                ) {
                    track.pause()
                    track.flush()
                    track.stop()
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Ignored exception while stopping AudioTrack: ${e.message}", e)
        }
    }

    /**
     * Releases native C++ pointers and Android audio resources.
     */
    override fun close() {
        release()
    }

    fun release() {
        if (!isReleased.compareAndSet(false, true)) return

        stopPlayback()

        audioTrack?.let {
            try {
                it.release()
            } catch (e: Exception) {
                AppLogger.w(TAG, "Error releasing AudioTrack: ${e.message}", e)
            }
        }
        audioTrack = null

        offlineTts?.let {
            try {
                it.release()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error releasing OfflineTts JNI pointer: ${e.message}", e)
            }
        }
        offlineTts = null
        activeModelPath = null
        AppLogger.i(TAG, "LocalTtsEngine resources released.")
    }

    private fun getOrCreateAudioTrack(sampleRate: Int): AudioTrack {
        val existing = audioTrack
        if (existing != null &&
            existing.state == AudioTrack.STATE_INITIALIZED &&
            currentTrackSampleRate == sampleRate
        ) {
            return existing
        }

        try {
            existing?.release()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error releasing old AudioTrack", e)
        }

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(sampleRate * 4) // At least 1 second buffer

        val newTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = newTrack
        currentTrackSampleRate = sampleRate
        return newTrack
    }

    @Throws(IOException::class)
    private fun copyAssetsRecursively(assetPath: String, destFile: File) {
        val assetManager = appContext.assets
        val childEntries = try {
            assetManager.list(assetPath)
        } catch (_: Exception) {
            null
        }

        if (!childEntries.isNullOrEmpty()) {
            if (!destFile.exists() && !destFile.mkdirs()) {
                throw IOException("Failed to create directory: ${destFile.absolutePath}")
            }
            for (entry in childEntries) {
                val childAssetPath = "$assetPath/$entry"
                val childDestFile = File(destFile, entry)
                copyAssetsRecursively(childAssetPath, childDestFile)
            }
        } else {
            destFile.parentFile?.mkdirs()
            try {
                assetManager.open(assetPath).use { inputStream ->
                    FileOutputStream(destFile).use { outputStream ->
                        inputStream.copyTo(outputStream, bufferSize = 8192)
                    }
                }
            } catch (e: Exception) {
                AppLogger.d(TAG, "Asset not found as file, skipped: $assetPath (${e.message})")
            }
        }
    }
}
