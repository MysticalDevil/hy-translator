package org.devil.hytranslator.data.repository

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import org.devil.hytranslator.domain.model.VoiceInputEvent
import org.devil.hytranslator.domain.model.VoiceInputState
import org.devil.hytranslator.domain.repository.VoiceInputRepository
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.min
import kotlin.math.sqrt

class SherpaOnnxVoiceInputRepository(
    private val nativeLoader: (String) -> Unit = { libraryName ->
        System.loadLibrary(libraryName)
    },
    private val sessionFactory: (
        assetPath: String,
        onEvent: (VoiceInputEvent) -> Unit,
    ) -> VoiceInputSession = { assetPath, onEvent ->
        AudioRecordSherpaOnnxVoiceInputSession(
            assetPath = assetPath,
            onEvent = onEvent,
        )
    },
) : VoiceInputRepository {
    private var activeSession: VoiceInputSession? = null

    override suspend fun start(
        assetPath: String,
        onEvent: (VoiceInputEvent) -> Unit,
    ): VoiceInputState {
        val missingModelFile = missingSherpaOnnxModelFile(assetPath)
        if (missingModelFile != null) {
            return VoiceInputState.Error("Missing sherpa-onnx ASR file: $missingModelFile")
        }

        try {
            nativeLoader(SHERPA_ONNX_JNI_LIBRARY)
        } catch (e: UnsatisfiedLinkError) {
            return VoiceInputState.Error(
                "sherpa-onnx native runtime is not installed. Run scripts/setup-sherpa-onnx-android.sh",
            )
        }

        stop()
        val session = sessionFactory(assetPath, onEvent)
        val started = session.start()
        if (started.isFailure) {
            session.stop()
            return VoiceInputState.Error(
                started.exceptionOrNull()?.message ?: "Failed to start sherpa-onnx ASR",
            )
        }
        activeSession = session
        return VoiceInputState.Listening
    }

    override fun stop() {
        activeSession?.stop()
        activeSession = null
    }

    companion object {
        const val SHERPA_ONNX_JNI_LIBRARY = "sherpa-onnx-jni"
        val REQUIRED_MODEL_FILES = listOf(
            "encoder-epoch-99-avg-1.onnx",
            "decoder-epoch-99-avg-1.onnx",
            "joiner-epoch-99-avg-1.onnx",
            "tokens.txt",
        )

        fun missingSherpaOnnxModelFile(assetPath: String): String? =
            REQUIRED_MODEL_FILES.firstOrNull { fileName ->
                !File(assetPath, fileName).isFile
            }
    }
}

interface VoiceInputSession {
    fun start(): Result<Unit>
    fun stop()
}

private class AudioRecordSherpaOnnxVoiceInputSession(
    private val assetPath: String,
    private val onEvent: (VoiceInputEvent) -> Unit,
) : VoiceInputSession {
    private val sampleRateInHz = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private var audioRecord: AudioRecord? = null
    private var recognizer: OnlineRecognizer? = null
    private var worker: Thread? = null

    @Volatile
    private var recording = false

    @SuppressLint("MissingPermission")
    override fun start(): Result<Unit> = runCatching {
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        check(minBufferSize > 0) {
            "AudioRecord cannot provide a valid microphone buffer"
        }

        val nextRecognizer = OnlineRecognizer(
            assetManager = null,
            config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = sampleRateInHz, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = File(assetPath, "encoder-epoch-99-avg-1.onnx").absolutePath,
                        decoder = File(assetPath, "decoder-epoch-99-avg-1.onnx").absolutePath,
                        joiner = File(assetPath, "joiner-epoch-99-avg-1.onnx").absolutePath,
                    ),
                    tokens = File(assetPath, "tokens.txt").absolutePath,
                    numThreads = 2,
                    modelType = "zipformer",
                ),
                endpointConfig = EndpointConfig(
                    rule1 = EndpointRule(false, 2.4f, 0.0f),
                    rule2 = EndpointRule(true, 1.4f, 0.0f),
                    rule3 = EndpointRule(false, 0.0f, 20.0f),
                ),
                enableEndpoint = true,
            ),
        )
        val nextAudioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRateInHz,
            channelConfig,
            audioFormat,
            minBufferSize * 2,
        )
        check(nextAudioRecord.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord failed to initialize"
        }

        recognizer = nextRecognizer
        audioRecord = nextAudioRecord
        recording = true
        nextAudioRecord.startRecording()
        worker = thread(start = true, name = "sherpa-onnx-asr") {
            runCatching {
                processSamples(nextRecognizer, nextAudioRecord)
                onEvent(VoiceInputEvent.Stopped)
            }.onFailure { error ->
                recording = false
                onEvent(VoiceInputEvent.Error(error.message ?: "sherpa-onnx ASR runtime failed"))
            }
        }
    }

    override fun stop() {
        recording = false
        worker?.join(STOP_JOIN_TIMEOUT_MS)
        worker = null
        audioRecord?.runCatching {
            stop()
            release()
        }
        audioRecord = null
        recognizer?.release()
        recognizer = null
    }

    private fun processSamples(recognizer: OnlineRecognizer, audioRecord: AudioRecord) {
        SherpaOnnxStreamingDecoder(recognizer).decodeAudioRecord(
            audioRecord = audioRecord,
            recording = { recording },
            sampleRate = sampleRateInHz,
            onEvent = onEvent,
        )
    }

    private companion object {
        const val STOP_JOIN_TIMEOUT_MS = 500L
    }
}

class SherpaOnnxStreamingFileTranscriber(
    private val nativeLoader: (String) -> Unit = { libraryName ->
        System.loadLibrary(libraryName)
    },
) {
    fun transcribe(
        assetPath: String,
        samples: FloatArray,
        sampleRate: Int,
    ): String {
        val missingModelFile = SherpaOnnxVoiceInputRepository.missingSherpaOnnxModelFile(assetPath)
        require(missingModelFile == null) {
            "Missing sherpa-onnx ASR file: $missingModelFile"
        }
        nativeLoader(SHERPA_ONNX_JNI_LIBRARY)
        val recognizer = createRecognizer(assetPath, sampleRate)
        return try {
            SherpaOnnxStreamingDecoder(recognizer).decodeSamples(samples, sampleRate)
        } finally {
            recognizer.release()
        }
    }

    private fun createRecognizer(assetPath: String, sampleRate: Int): OnlineRecognizer =
        OnlineRecognizer(
            assetManager = null,
            config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = sampleRate, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = File(assetPath, "encoder-epoch-99-avg-1.onnx").absolutePath,
                        decoder = File(assetPath, "decoder-epoch-99-avg-1.onnx").absolutePath,
                        joiner = File(assetPath, "joiner-epoch-99-avg-1.onnx").absolutePath,
                    ),
                    tokens = File(assetPath, "tokens.txt").absolutePath,
                    numThreads = 2,
                    modelType = "zipformer",
                ),
                endpointConfig = EndpointConfig(
                    rule1 = EndpointRule(false, 2.4f, 0.0f),
                    rule2 = EndpointRule(true, 1.4f, 0.0f),
                    rule3 = EndpointRule(false, 0.0f, 20.0f),
                ),
                enableEndpoint = true,
            ),
        )

    private companion object {
        const val SHERPA_ONNX_JNI_LIBRARY = SherpaOnnxVoiceInputRepository.SHERPA_ONNX_JNI_LIBRARY
    }
}

private class SherpaOnnxStreamingDecoder(
    private val recognizer: OnlineRecognizer,
) {
    fun decodeAudioRecord(
        audioRecord: AudioRecord,
        recording: () -> Boolean,
        sampleRate: Int,
        onEvent: (VoiceInputEvent) -> Unit,
    ) {
        val stream = recognizer.createStream()
        try {
            val buffer = ShortArray((0.1 * sampleRate).toInt())
            var committedText = ""
            while (recording()) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                check(read >= 0) {
                    "AudioRecord read failed: $read"
                }
                if (read <= 0) continue

                val samples = FloatArray(read) { index -> buffer[index] / PCM_16_SCALE }
                onEvent(VoiceInputEvent.Level(samples.rms()))
                committedText = acceptChunk(
                    stream = stream,
                    samples = samples,
                    sampleRate = sampleRate,
                    committedText = committedText,
                    onEvent = onEvent,
                )
            }
        } finally {
            stream.release()
        }
    }

    fun decodeSamples(
        samples: FloatArray,
        sampleRate: Int,
        chunkSize: Int = (0.1 * sampleRate).toInt(),
    ): String {
        val stream = recognizer.createStream()
        try {
            var committedText = ""
            var offset = 0
            while (offset < samples.size) {
                val end = min(offset + chunkSize, samples.size)
                committedText = acceptChunk(
                    stream = stream,
                    samples = samples.copyOfRange(offset, end),
                    sampleRate = sampleRate,
                    committedText = committedText,
                    onEvent = {},
                )
                offset = end
            }

            stream.acceptWaveform(FloatArray(sampleRate), sampleRate)
            stream.inputFinished()
            while (recognizer.isReady(stream)) {
                recognizer.decode(stream)
            }
            val finalText = recognizer.getResult(stream).text
            return joinAsrText(committedText, finalText)
        } finally {
            stream.release()
        }
    }

    private fun acceptChunk(
        stream: OnlineStream,
        samples: FloatArray,
        sampleRate: Int,
        committedText: String,
        onEvent: (VoiceInputEvent) -> Unit,
    ): String {
        stream.acceptWaveform(samples, sampleRate)
        while (recognizer.isReady(stream)) {
            recognizer.decode(stream)
        }

        val partialText = recognizer.getResult(stream).text
        if (partialText.isNotBlank()) {
            onEvent(VoiceInputEvent.Partial(joinAsrText(committedText, partialText)))
        }

        if (!recognizer.isEndpoint(stream)) return committedText
        if (partialText.isBlank()) {
            recognizer.reset(stream)
            return committedText
        }

        val nextCommittedText = joinAsrText(committedText, partialText)
        onEvent(VoiceInputEvent.Final(nextCommittedText))
        recognizer.reset(stream)
        return nextCommittedText
    }

    private fun FloatArray.rms(): Float {
        if (isEmpty()) return 0f
        val meanSquare = fold(0f) { sum, sample -> sum + sample * sample } / size
        return sqrt(meanSquare).coerceIn(0f, 1f)
    }

    private fun joinAsrText(prefix: String, suffix: String): String =
        when {
            prefix.isBlank() -> suffix
            suffix.isBlank() -> prefix
            else -> "$prefix $suffix"
        }

    private companion object {
        const val PCM_16_SCALE = 32768.0f
    }
}
