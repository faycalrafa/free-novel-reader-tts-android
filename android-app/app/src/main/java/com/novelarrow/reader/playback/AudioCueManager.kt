package com.novelarrow.reader.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.sin

/**
 * Manages low-latency audio cues for reader and playback events:
 * 1. Chapter change: Small crisp bip.
 * 2. Timer stop: Gentle descending tone when sleep timer expires.
 * 3. Folder done: Distinctive completion chime when all chapters/tracks in a folder/novel finish.
 *
 * Uses pre-synthesized PCM WAV files loaded into Android's low-latency [SoundPool] with USAGE_MEDIA,
 * plus a fallback to a single managed [ToneGenerator] instance to ensure 100% reliability and
 * strictly avoid native AudioTrack exhaustion.
 */
object AudioCueManager {

    private const val TAG = "AudioCueManager"
    private const val SAMPLE_RATE = 44100

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var soundPool: SoundPool? = null
    private var toneGenerator: ToneGenerator? = null
    private var settingsRepository: SettingsRepository? = null

    private var chapterChangeSoundId: Int = 0
    private var timerStopSoundId: Int = 0
    private var folderDoneSoundId: Int = 0

    @Volatile
    private var isInitialized = false

    fun initialize(context: Context, settingsRepo: SettingsRepository? = null) {
        if (isInitialized) return
        val appContext = context.applicationContext
        settingsRepository = settingsRepo ?: SettingsRepository(appContext)

        scope.launch {
            try {
                initAudioTrackEngine(appContext)
                isInitialized = true
                AppLogger.i(TAG, "AudioCueManager successfully initialized with low-latency SoundPool")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to initialize SoundPool engine, initializing ToneGenerator fallback: ${e.message}", e)
                initToneGeneratorFallback()
                isInitialized = true
            }
        }
    }

    private fun initAudioTrackEngine(context: Context) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val sp = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttributes)
            .build()
        soundPool = sp

        val soundDir = File(context.cacheDir, "sound_cues").apply {
            if (!exists()) mkdirs()
        }

        val chapterFile = File(soundDir, "cue_chapter_change.wav")
        if (!chapterFile.exists() || chapterFile.length() == 0L) {
            writeWavFile(chapterFile, generateChapterChangePcm())
        }
        chapterChangeSoundId = sp.load(chapterFile.absolutePath, 1)

        val timerFile = File(soundDir, "cue_timer_stop.wav")
        if (!timerFile.exists() || timerFile.length() == 0L) {
            writeWavFile(timerFile, generateTimerStopPcm())
        }
        timerStopSoundId = sp.load(timerFile.absolutePath, 1)

        val folderFile = File(soundDir, "cue_folder_done.wav")
        if (!folderFile.exists() || folderFile.length() == 0L) {
            writeWavFile(folderFile, generateFolderDonePcm())
        }
        folderDoneSoundId = sp.load(folderFile.absolutePath, 1)
    }

    private fun initToneGeneratorFallback() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create fallback ToneGenerator: ${e.message}", e)
        }
    }

    private fun isSoundEnabled(): Boolean {
        return settingsRepository?.getSettings()?.soundCuesEnabled != false
    }

    /**
     * Plays a small, gentle bip when advancing or changing chapters.
     */
    fun playChapterChange() {
        if (!isSoundEnabled()) return
        val sp = soundPool
        val id = chapterChangeSoundId
        if (sp != null && id > 0) {
            val streamId = sp.play(id, 0.70f, 0.70f, 1, 0, 1.0f)
            if (streamId != 0) {
                AppLogger.d(TAG, "Played chapter change bip via SoundPool (stream $streamId)")
                return
            }
        }
        playFallbackTone(ToneGenerator.TONE_PROP_BEEP, 75)
    }

    /**
     * Plays a gentle descending two-tone chime when the sleep timer expires.
     */
    fun playTimerStop() {
        if (!isSoundEnabled()) return
        val sp = soundPool
        val id = timerStopSoundId
        if (sp != null && id > 0) {
            val streamId = sp.play(id, 0.65f, 0.65f, 1, 0, 1.0f)
            if (streamId != 0) {
                AppLogger.d(TAG, "Played timer stop cue via SoundPool (stream $streamId)")
                return
            }
        }
        playFallbackTone(ToneGenerator.TONE_PROP_BEEP2, 180)
    }

    /**
     * Plays an ascending completion chime when all chapters or audio tracks in a folder/novel are done.
     */
    fun playFolderDone() {
        if (!isSoundEnabled()) return
        val sp = soundPool
        val id = folderDoneSoundId
        if (sp != null && id > 0) {
            val streamId = sp.play(id, 0.75f, 0.75f, 1, 0, 1.0f)
            if (streamId != 0) {
                AppLogger.d(TAG, "Played folder completion cue via SoundPool (stream $streamId)")
                return
            }
        }
        playFallbackTone(ToneGenerator.TONE_PROP_ACK, 320)
    }

    private fun playFallbackTone(toneType: Int, durationMs: Int) {
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            }
            toneGenerator?.startTone(toneType, durationMs)
            AppLogger.d(TAG, "Played fallback tone $toneType ($durationMs ms)")
        } catch (e: Exception) {
            AppLogger.w(TAG, "Unable to play audio cue via fallback ToneGenerator: ${e.message}", e)
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        toneGenerator?.release()
        toneGenerator = null
        isInitialized = false
    }

    // --- Waveform Synthesis (16-bit PCM, 44.1kHz, Mono) ---

    /**
     * 880 Hz crisp sine bip (~75 ms) with 5ms ramp-up and 30ms exponential decay.
     */
    private fun generateChapterChangePcm(): ShortArray {
        val durationSec = 0.075
        val totalSamples = (SAMPLE_RATE * durationSec).toInt()
        val pcm = ShortArray(totalSamples)
        val freq = 880.0
        val peak = 32767 * 0.65

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val rawSine = sin(2.0 * Math.PI * freq * t)
            val envelope = when {
                i < 220 -> (i.toDouble() / 220.0) // 5ms fade-in
                i > totalSamples - 1320 -> exp(-4.0 * (i - (totalSamples - 1320)).toDouble() / 1320.0) // decay
                else -> 1.0
            }
            pcm[i] = (rawSine * peak * envelope).toInt().coerceIn(-32768, 32767).toShort()
        }
        return pcm
    }

    /**
     * Descending two-tone soothing chime: 587.33 Hz (D5, 90ms) -> 440 Hz (A4, 150ms).
     */
    private fun generateTimerStopPcm(): ShortArray {
        val part1Samples = (SAMPLE_RATE * 0.090).toInt()
        val part2Samples = (SAMPLE_RATE * 0.150).toInt()
        val totalSamples = part1Samples + part2Samples
        val pcm = ShortArray(totalSamples)
        val peak = 32767 * 0.50

        // Tone 1: 587 Hz
        for (i in 0 until part1Samples) {
            val t = i.toDouble() / SAMPLE_RATE
            val sine = sin(2.0 * Math.PI * 587.33 * t)
            val env = when {
                i < 250 -> i.toDouble() / 250.0
                i > part1Samples - 400 -> exp(-3.0 * (i - (part1Samples - 400)).toDouble() / 400.0)
                else -> 1.0
            }
            pcm[i] = (sine * peak * env).toInt().coerceIn(-32768, 32767).toShort()
        }

        // Tone 2: 440 Hz
        for (i in 0 until part2Samples) {
            val t = i.toDouble() / SAMPLE_RATE
            val sine = sin(2.0 * Math.PI * 440.0 * t)
            val env = when {
                i < 250 -> i.toDouble() / 250.0
                i > part2Samples - 2200 -> exp(-3.5 * (i - (part2Samples - 2200)).toDouble() / 2200.0)
                else -> 1.0
            }
            pcm[part1Samples + i] = (sine * peak * env).toInt().coerceIn(-32768, 32767).toShort()
        }
        return pcm
    }

    /**
     * Ascending 3-note arpeggio completion chime: C5 (523 Hz) -> E5 (659 Hz) -> G5 (784 Hz).
     */
    private fun generateFolderDonePcm(): ShortArray {
        val noteSamples = (SAMPLE_RATE * 0.090).toInt()
        val lastNoteSamples = (SAMPLE_RATE * 0.160).toInt()
        val totalSamples = noteSamples * 2 + lastNoteSamples
        val pcm = ShortArray(totalSamples)
        val notes = listOf(523.25, 659.25, 783.99)
        val peak = 32767 * 0.60

        var offset = 0
        for ((idx, freq) in notes.withIndex()) {
            val len = if (idx == 2) lastNoteSamples else noteSamples
            for (i in 0 until len) {
                val t = i.toDouble() / SAMPLE_RATE
                val sine = sin(2.0 * Math.PI * freq * t)
                val env = when {
                    i < 200 -> i.toDouble() / 200.0
                    i > len - 600 -> exp(-3.5 * (i - (len - 600)).toDouble() / 600.0)
                    else -> 1.0
                }
                pcm[offset + i] = (sine * peak * env).toInt().coerceIn(-32768, 32767).toShort()
            }
            offset += len
        }
        return pcm
    }

    private fun writeWavFile(file: File, pcmData: ShortArray) {
        val byteDataLength = pcmData.size * 2
        val totalDataLen = byteDataLength + 36
        val buffer = ByteBuffer.allocate(44 + byteDataLength).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk
        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.putInt(totalDataLen)
        buffer.put('W'.code.toByte())
        buffer.put('A'.code.toByte())
        buffer.put('V'.code.toByte())
        buffer.put('E'.code.toByte())

        // fmt chunk
        buffer.put('f'.code.toByte())
        buffer.put('m'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put(' '.code.toByte())
        buffer.putInt(16) // Subchunk1Size
        buffer.putShort(1) // PCM format
        buffer.putShort(1) // Mono
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(SAMPLE_RATE * 1 * 2) // ByteRate
        buffer.putShort(2) // BlockAlign
        buffer.putShort(16) // BitsPerSample

        // data chunk
        buffer.put('d'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.putInt(byteDataLength)

        for (sample in pcmData) {
            buffer.putShort(sample)
        }

        FileOutputStream(file).use { fos ->
            fos.write(buffer.array())
            fos.flush()
        }
    }
}
