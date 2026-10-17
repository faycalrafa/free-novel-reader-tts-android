package com.novelarrow.reader.playback.local

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
 * Manages model extraction, native JNI pointers, and AudioTrack PCM float streaming.
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

    private var offlineTts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private var currentTrackSampleRate: Int = 0

    val isInitialized: Boolean
        get() = offlineTts != null && !isReleased.get()

    /**
     * Initializes the engine by copying required assets to internal storage (for POSIX filesystem access)
     * and allocating the native Sherpa-ONNX OfflineTts pointer.
     * Safe to call multiple times; subsequent calls are no-ops once initialized.
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            if (isReleased.get()) {
                val err = IllegalStateException("LocalTtsEngine has already been released.")
                AppLogger.e(TAG, err.message ?: "Released", err)
                return@withContext Result.failure(err)
            }
            if (offlineTts != null) return@withContext Result.success(Unit)

            try {
                val targetDir = File(appContext.filesDir, modelDirName)
                val modelFile = File(targetDir, modelFileName)

                // Copy assets to internal storage if not already extracted
                if (!modelFile.exists()) {
                    AppLogger.i(TAG, "Extracting TTS model assets to ${targetDir.absolutePath}...")
                    copyAssetsRecursively(modelDirName, targetDir)
                }

                val vitsConfig = OfflineTtsVitsModelConfig(
                    model = File(targetDir, modelFileName).absolutePath,
                    lexicon = "",
                    tokens = File(targetDir, "tokens.txt").absolutePath,
                    dataDir = File(targetDir, "espeak-ng-data").absolutePath,
                    dictDir = "",
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                )

                val modelConfig = OfflineTtsModelConfig(
                    vits = vitsConfig,
                    numThreads = numThreads,
                    debug = false,
                    provider = "cpu"
                )

                val config = OfflineTtsConfig(
                    model = modelConfig,
                    ruleFsts = "",
                    maxNumSentences = 1
                )

                // Allocate native TTS instance (pass null for AssetManager to use POSIX file paths)
                val tts = OfflineTts(assetManager = null, config = config)
                offlineTts = tts
                AppLogger.i(TAG, "Sherpa-ONNX OfflineTts initialized. Sample rate: ${tts.sampleRate()} Hz, Speakers: ${tts.numSpeakers()}")
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

        // Ensure initialized before synthesis
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
     * Synthesizes text and plays it immediately through an Android AudioTrack.
     */
    suspend fun speak(
        text: String,
        speed: Float = 1.0f,
        speakerId: Int = defaultSpeakerId
    ) = withContext(Dispatchers.Default) {
        val audioData = generateAudio(text, speed, speakerId)
        if (audioData.samples.isEmpty()) return@withContext

        engineMutex.withLock {
            check(!isReleased.get()) { "Cannot play audio: LocalTtsEngine is released." }
            val track = getOrCreateAudioTrack(audioData.sampleRate)

            try {
                track.play()

                // Stream chunks so coroutine cancellation can interrupt playback promptly
                val chunkSize = 4096
                var offset = 0
                val samples = audioData.samples

                while (offset < samples.size) {
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
            } catch (e: CancellationException) {
                AppLogger.d(TAG, "AudioTrack playback canceled")
                track.pause()
                track.flush()
                throw e
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Playback failure in speak(): ${t.message}", t)
                throw t
            } finally {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
            }
        }
    }

    /**
     * Immediately halts ongoing AudioTrack playback.
     */
    fun stopPlayback() {
        try {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED &&
                    track.playState == AudioTrack.PLAYSTATE_PLAYING
                ) {
                    track.pause()
                    track.flush()
                }
            }
        } catch (e: IllegalStateException) {
            AppLogger.w(TAG, "Ignored IllegalStateException while stopping AudioTrack", e)
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

        existing?.release()

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
        val childEntries = assetManager.list(assetPath)

        if (!childEntries.isNullOrEmpty()) {
            // Directory node
            if (!destFile.exists() && !destFile.mkdirs()) {
                throw IOException("Failed to create directory: ${destFile.absolutePath}")
            }
            for (entry in childEntries) {
                val childAssetPath = "$assetPath/$entry"
                val childDestFile = File(destFile, entry)
                copyAssetsRecursively(childAssetPath, childDestFile)
            }
        } else {
            // Leaf file node
            destFile.parentFile?.mkdirs()
            assetManager.open(assetPath).use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream, bufferSize = 8192)
                }
            }
        }
    }
}
