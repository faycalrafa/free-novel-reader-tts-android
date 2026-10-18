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
    val description: String = "",
    val speakerId: Int = 0,
    val voicesDownloadUrl: String? = null,
    val tokensDownloadUrl: String? = null,
    val lexiconDownloadUrl: String? = null
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
            sizeBytes = 63_200_000L,
            modelFileName = "en_US-lessac-medium.onnx",
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/vits-piper-en_US-lessac-medium/resolve/main/en_US-lessac-medium.onnx",
            configDownloadUrl = "https://huggingface.co/csukuangfj/vits-piper-en_US-lessac-medium/resolve/main/tokens.txt",
            sampleAudioUrl = "https://rhasspy.github.io/piper-samples/samples/en/en_US/lessac/medium/speaker_0.mp3",
            description = "Natural, clear American voice with balanced intonation for storytelling."
        ),
        NeuralVoiceInfo(
            id = "piper_en_us_amy_medium",
            engine = "PIPER",
            name = "Amy (US)",
            gender = "Female",
            quality = "Studio Medium",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 63_200_000L,
            modelFileName = "en_US-amy-medium.onnx",
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/vits-piper-en_US-amy-medium/resolve/main/en_US-amy-medium.onnx",
            configDownloadUrl = "https://huggingface.co/csukuangfj/vits-piper-en_US-amy-medium/resolve/main/tokens.txt",
            sampleAudioUrl = "https://rhasspy.github.io/piper-samples/samples/en/en_US/amy/medium/speaker_0.mp3",
            description = "Warm and expressive American narrator with lively cadence."
        ),
        NeuralVoiceInfo(
            id = "piper_en_gb_alan_medium",
            engine = "PIPER",
            name = "Alan (UK)",
            gender = "Male",
            quality = "Studio Medium",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 63_200_000L,
            modelFileName = "en_GB-alan-medium.onnx",
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/vits-piper-en_GB-alan-medium/resolve/main/en_GB-alan-medium.onnx",
            configDownloadUrl = "https://huggingface.co/csukuangfj/vits-piper-en_GB-alan-medium/resolve/main/tokens.txt",
            sampleAudioUrl = "https://rhasspy.github.io/piper-samples/samples/en/en_GB/alan/medium/speaker_0.mp3",
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
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/vits-piper-en_US-danny-low/resolve/main/en_US-danny-low.onnx",
            configDownloadUrl = "https://huggingface.co/csukuangfj/vits-piper-en_US-danny-low/resolve/main/tokens.txt",
            sampleAudioUrl = "https://rhasspy.github.io/piper-samples/samples/en/en_US/danny/low/speaker_0.mp3",
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
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/vits-piper-fr_FR-siwis-medium/resolve/main/fr_FR-siwis-medium.onnx",
            configDownloadUrl = "https://huggingface.co/csukuangfj/vits-piper-fr_FR-siwis-medium/resolve/main/tokens.txt",
            sampleAudioUrl = "https://rhasspy.github.io/piper-samples/samples/fr/fr_FR/siwis/medium/speaker_0.mp3",
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
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/vits-piper-fr_FR-upmc-medium/resolve/main/fr_FR-upmc-medium.onnx",
            configDownloadUrl = "https://huggingface.co/csukuangfj/vits-piper-fr_FR-upmc-medium/resolve/main/tokens.txt",
            sampleAudioUrl = "https://rhasspy.github.io/piper-samples/samples/fr/fr_FR/upmc/medium/speaker_0.mp3",
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
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/vits-piper-fr_FR-gilles-low/resolve/main/fr_FR-gilles-low.onnx",
            configDownloadUrl = "https://huggingface.co/csukuangfj/vits-piper-fr_FR-gilles-low/resolve/main/tokens.txt",
            sampleAudioUrl = "https://rhasspy.github.io/piper-samples/samples/fr/fr_FR/gilles/low/speaker_0.mp3",
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
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/vits-piper-ar_JO-kareem-medium/resolve/main/ar_JO-kareem-medium.onnx",
            configDownloadUrl = "https://huggingface.co/csukuangfj/vits-piper-ar_JO-kareem-medium/resolve/main/tokens.txt",
            sampleAudioUrl = "https://rhasspy.github.io/piper-samples/samples/ar/ar_JO/kareem/medium/speaker_0.mp3",
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
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/vits-piper-ar_JO-kareem-low/resolve/main/ar_JO-kareem-low.onnx",
            configDownloadUrl = "https://huggingface.co/csukuangfj/vits-piper-ar_JO-kareem-low/resolve/main/tokens.txt",
            sampleAudioUrl = "https://rhasspy.github.io/piper-samples/samples/ar/ar_JO/kareem/low/speaker_0.mp3",
            description = "Compact Arabic neural voice for lightweight listening on older phones."
        )
    )

    val KOKORO_VOICES: List<NeuralVoiceInfo> = listOf(
        // English (Kokoro INT8 Multi-Lang v1.0)
        NeuralVoiceInfo(
            id = "kokoro_af_heart",
            engine = "KOKORO",
            name = "Heart (US)",
            gender = "Female",
            quality = "Studio INT8",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 148_000_000L,
            modelFileName = "model.int8.onnx",
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/model.int8.onnx",
            voicesDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/voices.bin",
            tokensDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/tokens.txt",
            lexiconDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/lexicon-us-en.txt",
            speakerId = 3,
            sampleAudioUrl = "https://huggingface.co/spaces/hexgrad/Kokoro-TTS/resolve/main/samples/af_heart.mp3",
            description = "Kokoro's flagship warm female voice with human-grade prosody."
        ),
        NeuralVoiceInfo(
            id = "kokoro_af_bella",
            engine = "KOKORO",
            name = "Bella (US)",
            gender = "Female",
            quality = "Studio INT8",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 148_000_000L,
            modelFileName = "model.int8.onnx",
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/model.int8.onnx",
            voicesDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/voices.bin",
            tokensDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/tokens.txt",
            lexiconDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/lexicon-us-en.txt",
            speakerId = 2,
            sampleAudioUrl = "https://huggingface.co/spaces/hexgrad/Kokoro-TTS/resolve/main/samples/af_bella.mp3",
            description = "Soft, melodic American voice with gentle pacing."
        ),
        NeuralVoiceInfo(
            id = "kokoro_af_sarah",
            engine = "KOKORO",
            name = "Sarah (US)",
            gender = "Female",
            quality = "Studio INT8",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 148_000_000L,
            modelFileName = "model.int8.onnx",
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/model.int8.onnx",
            voicesDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/voices.bin",
            tokensDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/tokens.txt",
            lexiconDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/lexicon-us-en.txt",
            speakerId = 9,
            sampleAudioUrl = "https://huggingface.co/spaces/hexgrad/Kokoro-TTS/resolve/main/samples/af_sarah.mp3",
            description = "Crisp, articulate narrative voice suited for fantasy and sci-fi."
        ),
        NeuralVoiceInfo(
            id = "kokoro_af_sky",
            engine = "KOKORO",
            name = "Sky (US)",
            gender = "Female",
            quality = "Studio INT8",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 148_000_000L,
            modelFileName = "model.int8.onnx",
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/model.int8.onnx",
            voicesDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/voices.bin",
            tokensDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/tokens.txt",
            lexiconDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/lexicon-us-en.txt",
            speakerId = 10,
            sampleAudioUrl = "https://huggingface.co/spaces/hexgrad/Kokoro-TTS/resolve/main/samples/af_sky.mp3",
            description = "Youthful, energetic voice with vibrant pacing."
        ),
        NeuralVoiceInfo(
            id = "kokoro_am_adam",
            engine = "KOKORO",
            name = "Adam (US)",
            gender = "Male",
            quality = "Studio INT8",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 148_000_000L,
            modelFileName = "model.int8.onnx",
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/model.int8.onnx",
            voicesDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/voices.bin",
            tokensDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/tokens.txt",
            lexiconDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/lexicon-us-en.txt",
            speakerId = 11,
            sampleAudioUrl = "https://huggingface.co/spaces/hexgrad/Kokoro-TTS/resolve/main/samples/am_adam.mp3",
            description = "Deep, resonant male voice with commanding storytelling presence."
        ),
        NeuralVoiceInfo(
            id = "kokoro_am_michael",
            engine = "KOKORO",
            name = "Michael (US)",
            gender = "Male",
            quality = "Studio INT8",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 148_000_000L,
            modelFileName = "model.int8.onnx",
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/model.int8.onnx",
            voicesDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/voices.bin",
            tokensDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/tokens.txt",
            lexiconDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/lexicon-us-en.txt",
            speakerId = 16,
            sampleAudioUrl = "https://huggingface.co/spaces/hexgrad/Kokoro-TTS/resolve/main/samples/am_michael.mp3",
            description = "Warm baritone narrator with relaxed, natural cadence."
        ),
        NeuralVoiceInfo(
            id = "kokoro_bf_emma",
            engine = "KOKORO",
            name = "Emma (UK)",
            gender = "Female",
            quality = "Studio INT8",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 148_000_000L,
            modelFileName = "model.int8.onnx",
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/model.int8.onnx",
            voicesDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/voices.bin",
            tokensDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/tokens.txt",
            lexiconDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/lexicon-us-en.txt",
            speakerId = 21,
            sampleAudioUrl = "https://huggingface.co/spaces/hexgrad/Kokoro-TTS/resolve/main/samples/bf_emma.mp3",
            description = "Eloquent British voice with classic BBC-style narration."
        ),
        NeuralVoiceInfo(
            id = "kokoro_bm_george",
            engine = "KOKORO",
            name = "George (UK)",
            gender = "Male",
            quality = "Studio INT8",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 148_000_000L,
            modelFileName = "model.int8.onnx",
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/model.int8.onnx",
            voicesDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/voices.bin",
            tokensDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/tokens.txt",
            lexiconDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/lexicon-us-en.txt",
            speakerId = 26,
            sampleAudioUrl = "https://huggingface.co/spaces/hexgrad/Kokoro-TTS/resolve/main/samples/bm_george.mp3",
            description = "Distinguished British male voice ideal for historical and epic literature."
        ),

        // French (Kokoro INT8 Multi-Lang v1.0)
        NeuralVoiceInfo(
            id = "kokoro_ff_siwis",
            engine = "KOKORO",
            name = "Amélie (FR)",
            gender = "Female",
            quality = "Studio INT8",
            language = SupportedLanguage.FRENCH,
            sizeBytes = 148_000_000L,
            modelFileName = "model.int8.onnx",
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/model.int8.onnx",
            voicesDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/voices.bin",
            tokensDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/tokens.txt",
            lexiconDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/lexicon-us-en.txt",
            speakerId = 30,
            sampleAudioUrl = "https://huggingface.co/spaces/hexgrad/Kokoro-TTS/resolve/main/samples/ff_siwis.mp3",
            description = "Smooth Parisian French female voice synthesized by Kokoro v1.0."
        ),
        NeuralVoiceInfo(
            id = "kokoro_fm_pierre",
            engine = "KOKORO",
            name = "Pierre (FR)",
            gender = "Male",
            quality = "Studio INT8",
            language = SupportedLanguage.FRENCH,
            sizeBytes = 148_000_000L,
            modelFileName = "model.int8.onnx",
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/model.int8.onnx",
            voicesDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/voices.bin",
            tokensDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/tokens.txt",
            lexiconDownloadUrl = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/lexicon-us-en.txt",
            speakerId = 29,
            sampleAudioUrl = "https://huggingface.co/spaces/hexgrad/Kokoro-TTS/resolve/main/samples/fm_pierre.mp3",
            description = "Warm French baritone narrator with expressive dynamic range."
        )
    )

    val KITTEN_VOICES: List<NeuralVoiceInfo> = listOf(
        // English (KittenTTS Nano - csukuangfj/kitten-nano-en-v0_1-fp16)
        NeuralVoiceInfo(
            id = "kitten_micro_jasper",
            engine = "KITTEN",
            name = "Jasper (US)",
            gender = "Male",
            quality = "Nano 0.1",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 23_900_000L,
            modelFileName = "model.fp16.onnx",
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/model.fp16.onnx",
            voicesDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/voices.bin",
            tokensDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/tokens.txt",
            speakerId = 0,
            sampleAudioUrl = null,
            description = "Dynamic male narrator with clear articulation and balanced tone."
        ),
        NeuralVoiceInfo(
            id = "kitten_micro_bella",
            engine = "KITTEN",
            name = "Bella (US)",
            gender = "Female",
            quality = "Nano 0.1",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 23_900_000L,
            modelFileName = "model.fp16.onnx",
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/model.fp16.onnx",
            voicesDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/voices.bin",
            tokensDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/tokens.txt",
            speakerId = 1,
            sampleAudioUrl = null,
            description = "Expressive female voice with lively cadence and character depth."
        ),
        NeuralVoiceInfo(
            id = "kitten_micro_bruno",
            engine = "KITTEN",
            name = "Bruno (US)",
            gender = "Male",
            quality = "Nano 0.1",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 23_900_000L,
            modelFileName = "model.fp16.onnx",
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/model.fp16.onnx",
            voicesDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/voices.bin",
            tokensDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/tokens.txt",
            speakerId = 2,
            sampleAudioUrl = null,
            description = "Warm, balanced male voice suitable for long fantasy novels."
        ),
        NeuralVoiceInfo(
            id = "kitten_micro_luna",
            engine = "KITTEN",
            name = "Luna (US)",
            gender = "Female",
            quality = "Nano 0.1",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 23_900_000L,
            modelFileName = "model.fp16.onnx",
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/model.fp16.onnx",
            voicesDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/voices.bin",
            tokensDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/tokens.txt",
            speakerId = 3,
            sampleAudioUrl = null,
            description = "Smooth, natural female voice ideal for emotional storytelling."
        ),
        NeuralVoiceInfo(
            id = "kitten_micro_hugo",
            engine = "KITTEN",
            name = "Hugo (US)",
            gender = "Male",
            quality = "Nano 0.1",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 23_900_000L,
            modelFileName = "model.fp16.onnx",
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/model.fp16.onnx",
            voicesDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/voices.bin",
            tokensDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/tokens.txt",
            speakerId = 4,
            sampleAudioUrl = null,
            description = "Deep baritone male narrator with authoritative pacing."
        ),
        NeuralVoiceInfo(
            id = "kitten_micro_rosie",
            engine = "KITTEN",
            name = "Rosie (US)",
            gender = "Female",
            quality = "Nano 0.1",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 23_900_000L,
            modelFileName = "model.fp16.onnx",
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/model.fp16.onnx",
            voicesDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/voices.bin",
            tokensDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/tokens.txt",
            speakerId = 5,
            sampleAudioUrl = null,
            description = "Bright, energetic female narration with crisp dialogue delivery."
        ),
        NeuralVoiceInfo(
            id = "kitten_micro_leo",
            engine = "KITTEN",
            name = "Leo (US)",
            gender = "Male",
            quality = "Nano 0.1",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 23_900_000L,
            modelFileName = "model.fp16.onnx",
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/model.fp16.onnx",
            voicesDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/voices.bin",
            tokensDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/tokens.txt",
            speakerId = 6,
            sampleAudioUrl = null,
            description = "Modern male voice with neutral tone and fatigue-free listening."
        ),
        NeuralVoiceInfo(
            id = "kitten_micro_kiki",
            engine = "KITTEN",
            name = "Kiki (US)",
            gender = "Female",
            quality = "Nano 0.1",
            language = SupportedLanguage.ENGLISH,
            sizeBytes = 23_900_000L,
            modelFileName = "model.fp16.onnx",
            onnxDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/model.fp16.onnx",
            voicesDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/voices.bin",
            tokensDownloadUrl = "https://huggingface.co/csukuangfj/kitten-nano-en-v0_1-fp16/resolve/main/tokens.txt",
            speakerId = 7,
            sampleAudioUrl = null,
            description = "Clear and youthful female narrator with gentle pacing."
        )
    )

    fun getVoicesForEngine(engine: String, context: android.content.Context? = null): List<NeuralVoiceInfo> {
        val staticVoices = when (engine.uppercase()) {
            "PIPER" -> PIPER_VOICES
            "KOKORO" -> KOKORO_VOICES
            "KITTEN" -> KITTEN_VOICES
            else -> emptyList()
        }
        val customVoices = if (context != null) {
            CustomVoiceManager.getCustomVoices(context).filter { it.engine.equals(engine, ignoreCase = true) }
        } else emptyList()
        return staticVoices + customVoices
    }

    fun getVoiceById(id: String, context: android.content.Context? = null): NeuralVoiceInfo? {
        val customVoices = if (context != null) CustomVoiceManager.getCustomVoices(context) else emptyList()
        return (PIPER_VOICES + KOKORO_VOICES + KITTEN_VOICES + customVoices).firstOrNull { it.id == id }
    }
}
