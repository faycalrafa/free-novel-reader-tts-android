package com.novelarrow.reader.data.model

import com.novelarrow.reader.playback.SupportedLanguage
import java.util.Locale

data class NeuralVoiceInfo(
    val id: String,
    val engine: String, // "PIPER", "KOKORO", "KITTEN"
    val name: String,
    val gender: String, // "Female", "Male", "Neutral"
    val quality: String, // "High", "Medium", "Compact"
    val language: SupportedLanguage,
    val sizeBytes: Long,
    val modelFileName: String,
    val onnxDownloadUrl: String,
    val configDownloadUrl: String? = null,
    val sampleAudioUrl: String? = null,
    val description: String = ""
) {
    val formattedSize: String
        get() {
            val mb = sizeBytes.toDouble() / (1024.0 * 1024.0)
            return String.format(Locale.US, "%.1f MB", mb)
        }
}

object NeuralVoiceCatalog {

    val PIPER_VOICES: List<NeuralVoiceInfo> = listOf(
        // English (Piper)
        NeuralVoiceInfo(
            id = "piper_en_us_lessac_medium",
            engine = "PIPER",
            name = "Lessac (US)",
            gender = "Female",
            quality = "Studio Medium",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 63_400_000L,
            modelFileName = "en_US-lessac-medium.onnx",
            onnxDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx",
            configDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx.json",
            sampleAudioUrl = "https://rhasspy.github.io/piper-samples/samples/en/en_US/lessac/medium/sample.mp3",
            description = "Natural, clear American voice with balanced intonation for storytelling."
        ),
        NeuralVoiceInfo(
            id = "piper_en_us_amy_medium",
            engine = "PIPER",
            name = "Amy (US)",
            gender = "Female",
            quality = "Studio Medium",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 63_100_000L,
            modelFileName = "en_US-amy-medium.onnx",
            onnxDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/amy/medium/en_US-amy-medium.onnx",
            configDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/amy/medium/en_US-amy-medium.onnx.json",
            sampleAudioUrl = "https://rhasspy.github.io/piper-samples/samples/en/en_US/amy/medium/sample.mp3",
            description = "Warm and expressive American narrator with lively cadence."
        ),
        NeuralVoiceInfo(
            id = "piper_en_gb_alan_medium",
            engine = "PIPER",
            name = "Alan (UK)",
            gender = "Male",
            quality = "Studio Medium",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 63_000_000L,
            modelFileName = "en_GB-alan-medium.onnx",
            onnxDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_GB/alan/medium/en_GB-alan-medium.onnx",
            configDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_GB/alan/medium/en_GB-alan-medium.onnx.json",
            sampleAudioUrl = "https://rhasspy.github.io/piper-samples/samples/en/en_GB/alan/medium/sample.mp3",
            description = "Refined British English narrator with clear pronunciation."
        ),
        NeuralVoiceInfo(
            id = "piper_en_us_danny_low",
            engine = "PIPER",
            name = "Danny Compact (US)",
            gender = "Male",
            quality = "Ultra Compact",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 28_200_000L,
            modelFileName = "en_US-danny-low.onnx",
            onnxDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/danny/low/en_US-danny-low.onnx",
            configDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/danny/low/en_US-danny-low.onnx.json",
            sampleAudioUrl = "https://rhasspy.github.io/piper-samples/samples/en/en_US/danny/low/sample.mp3",
            description = "Extremely lightweight mobile model with minimal battery and memory overhead."
        ),

        // French (Piper)
        NeuralVoiceInfo(
            id = "piper_fr_fr_siwis_medium",
            engine = "PIPER",
            name = "Siwis (FR)",
            gender = "Female",
            quality = "Studio Medium",
            language = SupportedLanguage.FRENCH,
            sizeBytes = 63_200_000L,
            modelFileName = "fr_FR-siwis-medium.onnx",
            onnxDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/fr/fr_FR/siwis/medium/fr_FR-siwis-medium.onnx",
            configDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/fr/fr_FR/siwis/medium/fr_FR-siwis-medium.onnx.json",
            sampleAudioUrl = "https://rhasspy.github.io/piper-samples/samples/fr/fr_FR/siwis/medium/sample.mp3",
            description = "Natural French female voice recorded with pristine clarity."
        ),
        NeuralVoiceInfo(
            id = "piper_fr_fr_upmc_medium",
            engine = "PIPER",
            name = "UPMC (FR)",
            gender = "Female",
            quality = "Studio Medium",
            language = SupportedLanguage.FRENCH,
            sizeBytes = 63_500_000L,
            modelFileName = "fr_FR-upmc-medium.onnx",
            onnxDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/fr/fr_FR/upmc/medium/fr_FR-upmc-medium.onnx",
            configDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/fr/fr_FR/upmc/medium/fr_FR-upmc-medium.onnx.json",
            sampleAudioUrl = "https://rhasspy.github.io/piper-samples/samples/fr/fr_FR/upmc/medium/sample.mp3",
            description = "Standard Parisian French voice ideal for modern novels."
        ),
        NeuralVoiceInfo(
            id = "piper_fr_fr_gilles_low",
            engine = "PIPER",
            name = "Gilles Compact (FR)",
            gender = "Male",
            quality = "Ultra Compact",
            language = SupportedLanguage.FRENCH,
            sizeBytes = 28_500_000L,
            modelFileName = "fr_FR-gilles-low.onnx",
            onnxDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/fr/fr_FR/gilles/low/fr_FR-gilles-low.onnx",
            configDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/fr/fr_FR/gilles/low/fr_FR-gilles-low.onnx.json",
            sampleAudioUrl = "https://rhasspy.github.io/piper-samples/samples/fr/fr_FR/gilles/low/sample.mp3",
            description = "Lightweight French male voice optimized for rapid synthesis."
        ),

        // Arabic (Piper)
        NeuralVoiceInfo(
            id = "piper_ar_jo_kareem_medium",
            engine = "PIPER",
            name = "Kareem (العربية)",
            gender = "Male",
            quality = "Studio Medium",
            language = SupportedLanguage.ARABIC,
            sizeBytes = 63_300_000L,
            modelFileName = "ar_JO-kareem-medium.onnx",
            onnxDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/ar/ar_JO/kareem/medium/ar_JO-kareem-medium.onnx",
            configDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/ar/ar_JO/kareem/medium/ar_JO-kareem-medium.onnx.json",
            sampleAudioUrl = "https://rhasspy.github.io/piper-samples/samples/ar/ar_JO/kareem/medium/sample.mp3",
            description = "Classical Arabic (Fusha) neural voice with clear diacritical articulation."
        ),
        NeuralVoiceInfo(
            id = "piper_ar_jo_kareem_low",
            engine = "PIPER",
            name = "Kareem Compact (العربية)",
            gender = "Male",
            quality = "Ultra Compact",
            language = SupportedLanguage.ARABIC,
            sizeBytes = 28_400_000L,
            modelFileName = "ar_JO-kareem-low.onnx",
            onnxDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/ar/ar_JO/kareem/low/ar_JO-kareem-low.onnx",
            configDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/ar/ar_JO/kareem/low/ar_JO-kareem-low.onnx.json",
            sampleAudioUrl = "https://rhasspy.github.io/piper-samples/samples/ar/ar_JO/kareem/low/sample.mp3",
            description = "Compact Arabic neural voice for lightweight listening on older phones."
        )
    )

    val KOKORO_VOICES: List<NeuralVoiceInfo> = listOf(
        // English (Kokoro 82M)
        NeuralVoiceInfo(
            id = "kokoro_af_heart",
            engine = "KOKORO",
            name = "Heart (US)",
            gender = "Female",
            quality = "Studio 82M",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 88_500_000L,
            modelFileName = "kokoro-v1.0-q8.onnx",
            onnxDownloadUrl = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/onnx/model_q8.onnx",
            configDownloadUrl = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/config.json",
            sampleAudioUrl = "https://huggingface.co/spaces/hexgrad/Kokoro-TTS/resolve/main/samples/af_heart.mp3",
            description = "Kokoro's flagship warm female voice with human-grade prosody."
        ),
        NeuralVoiceInfo(
            id = "kokoro_af_bella",
            engine = "KOKORO",
            name = "Bella (US)",
            gender = "Female",
            quality = "Studio 82M",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 88_500_000L,
            modelFileName = "kokoro-v1.0-q8.onnx",
            onnxDownloadUrl = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/onnx/model_q8.onnx",
            sampleAudioUrl = "https://huggingface.co/spaces/hexgrad/Kokoro-TTS/resolve/main/samples/af_bella.mp3",
            description = "Soft, melodic American voice with gentle pacing."
        ),
        NeuralVoiceInfo(
            id = "kokoro_af_sarah",
            engine = "KOKORO",
            name = "Sarah (US)",
            gender = "Female",
            quality = "Studio 82M",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 88_500_000L,
            modelFileName = "kokoro-v1.0-q8.onnx",
            onnxDownloadUrl = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/onnx/model_q8.onnx",
            sampleAudioUrl = "https://huggingface.co/spaces/hexgrad/Kokoro-TTS/resolve/main/samples/af_sarah.mp3",
            description = "Crisp, articulate narrative voice suited for fantasy and sci-fi."
        ),
        NeuralVoiceInfo(
            id = "kokoro_af_sky",
            engine = "KOKORO",
            name = "Sky (US)",
            gender = "Female",
            quality = "Studio 82M",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 88_500_000L,
            modelFileName = "kokoro-v1.0-q8.onnx",
            onnxDownloadUrl = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/onnx/model_q8.onnx",
            sampleAudioUrl = "https://huggingface.co/spaces/hexgrad/Kokoro-TTS/resolve/main/samples/af_sky.mp3",
            description = "Youthful, energetic voice with vibrant pacing."
        ),
        NeuralVoiceInfo(
            id = "kokoro_am_adam",
            engine = "KOKORO",
            name = "Adam (US)",
            gender = "Male",
            quality = "Studio 82M",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 88_500_000L,
            modelFileName = "kokoro-v1.0-q8.onnx",
            onnxDownloadUrl = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/onnx/model_q8.onnx",
            sampleAudioUrl = "https://huggingface.co/spaces/hexgrad/Kokoro-TTS/resolve/main/samples/am_adam.mp3",
            description = "Deep, resonant male voice with commanding storytelling presence."
        ),
        NeuralVoiceInfo(
            id = "kokoro_am_michael",
            engine = "KOKORO",
            name = "Michael (US)",
            gender = "Male",
            quality = "Studio 82M",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 88_500_000L,
            modelFileName = "kokoro-v1.0-q8.onnx",
            onnxDownloadUrl = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/onnx/model_q8.onnx",
            sampleAudioUrl = "https://huggingface.co/spaces/hexgrad/Kokoro-TTS/resolve/main/samples/am_michael.mp3",
            description = "Warm baritone narrator with relaxed, natural cadence."
        ),
        NeuralVoiceInfo(
            id = "kokoro_bf_emma",
            engine = "KOKORO",
            name = "Emma (UK)",
            gender = "Female",
            quality = "Studio 82M",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 88_500_000L,
            modelFileName = "kokoro-v1.0-q8.onnx",
            onnxDownloadUrl = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/onnx/model_q8.onnx",
            sampleAudioUrl = "https://huggingface.co/spaces/hexgrad/Kokoro-TTS/resolve/main/samples/bf_emma.mp3",
            description = "Eloquent British voice with classic BBC-style narration."
        ),
        NeuralVoiceInfo(
            id = "kokoro_bm_george",
            engine = "KOKORO",
            name = "George (UK)",
            gender = "Male",
            quality = "Studio 82M",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 88_500_000L,
            modelFileName = "kokoro-v1.0-q8.onnx",
            onnxDownloadUrl = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/onnx/model_q8.onnx",
            sampleAudioUrl = "https://huggingface.co/spaces/hexgrad/Kokoro-TTS/resolve/main/samples/bm_george.mp3",
            description = "Distinguished British male voice ideal for historical and epic literature."
        ),

        // French (Kokoro 82M)
        NeuralVoiceInfo(
            id = "kokoro_ff_siwis",
            engine = "KOKORO",
            name = "Amélie (FR)",
            gender = "Female",
            quality = "Studio 82M",
            language = SupportedLanguage.FRENCH,
            sizeBytes = 88_500_000L,
            modelFileName = "kokoro-v1.0-q8.onnx",
            onnxDownloadUrl = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/onnx/model_q8.onnx",
            sampleAudioUrl = "https://huggingface.co/spaces/hexgrad/Kokoro-TTS/resolve/main/samples/ff_siwis.mp3",
            description = "Smooth Parisian French female voice synthesized by Kokoro v1.0."
        ),
        NeuralVoiceInfo(
            id = "kokoro_fm_pierre",
            engine = "KOKORO",
            name = "Pierre (FR)",
            gender = "Male",
            quality = "Studio 82M",
            language = SupportedLanguage.FRENCH,
            sizeBytes = 88_500_000L,
            modelFileName = "kokoro-v1.0-q8.onnx",
            onnxDownloadUrl = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/onnx/model_q8.onnx",
            sampleAudioUrl = "https://huggingface.co/spaces/hexgrad/Kokoro-TTS/resolve/main/samples/fm_pierre.mp3",
            description = "Warm French baritone narrator with expressive dynamic range."
        )
    )

    val KITTEN_VOICES: List<NeuralVoiceInfo> = listOf(
        // English (KittenTTS - Ultra compact)
        NeuralVoiceInfo(
            id = "kitten_natural_female",
            engine = "KITTEN",
            name = "Kitten Natural (US)",
            gender = "Female",
            quality = "Ultra Nano",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 28_500_000L,
            modelFileName = "kitten-nano-female.onnx",
            onnxDownloadUrl = "https://huggingface.co/KittenML/kitten-tts-nano/resolve/main/model.onnx",
            sampleAudioUrl = "https://huggingface.co/spaces/KingNish/Kitten-TTS/resolve/main/samples/female_natural.mp3",
            description = "Under 30MB ultra-lightweight neural model tuned for maximum battery life."
        ),
        NeuralVoiceInfo(
            id = "kitten_warm_male",
            engine = "KITTEN",
            name = "Kitten Warm (US)",
            gender = "Male",
            quality = "Ultra Nano",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 28_500_000L,
            modelFileName = "kitten-nano-male.onnx",
            onnxDownloadUrl = "https://huggingface.co/KittenML/kitten-tts-nano/resolve/main/model_male.onnx",
            sampleAudioUrl = "https://huggingface.co/spaces/KingNish/Kitten-TTS/resolve/main/samples/male_warm.mp3",
            description = "Compact male voice running at sub-50ms latency on mobile CPUs."
        ),
        NeuralVoiceInfo(
            id = "kitten_expressive_narrator",
            engine = "KITTEN",
            name = "Kitten Expressive (US)",
            gender = "Female",
            quality = "Micro Voice",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 42_000_000L,
            modelFileName = "kitten-micro-expressive.onnx",
            onnxDownloadUrl = "https://huggingface.co/KittenML/kitten-tts-mini/resolve/main/model.onnx",
            sampleAudioUrl = "https://huggingface.co/spaces/KingNish/Kitten-TTS/resolve/main/samples/narrator.mp3",
            description = "Compact 42MB voice delivering expressive dialogue and character changes."
        ),
        NeuralVoiceInfo(
            id = "kitten_clear_reader",
            engine = "KITTEN",
            name = "Kitten Clear (US)",
            gender = "Neutral",
            quality = "Ultra Nano",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 31_200_000L,
            modelFileName = "kitten-nano-clear.onnx",
            onnxDownloadUrl = "https://huggingface.co/KittenML/kitten-tts-nano/resolve/main/model_clear.onnx",
            sampleAudioUrl = "https://huggingface.co/spaces/KingNish/Kitten-TTS/resolve/main/samples/clear_reader.mp3",
            description = "Distraction-free neutral narrator with steady pacing for marathon reading sessions."
        )
    )

    fun getVoicesForEngine(engine: String): List<NeuralVoiceInfo> {
        return when (engine.uppercase()) {
            "PIPER" -> PIPER_VOICES
            "KOKORO" -> KOKORO_VOICES
            "KITTEN" -> KITTEN_VOICES
            else -> emptyList()
        }
    }

    fun getVoiceById(id: String): NeuralVoiceInfo? {
        return (PIPER_VOICES + KOKORO_VOICES + KITTEN_VOICES).firstOrNull { it.id == id }
    }
}
