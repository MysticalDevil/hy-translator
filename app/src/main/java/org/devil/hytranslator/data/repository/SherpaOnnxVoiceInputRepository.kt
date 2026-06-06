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
import org.devil.hytranslator.domain.model.VoiceInputState
import org.devil.hytranslator.domain.repository.VoiceInputRepository
import java.io.File
import kotlin.concurrent.thread

class SherpaOnnxVoiceInputRepository(
    private val nativeLoader: (String) -> Unit = { libraryName ->
        System.loadLibrary(libraryName)
    },
    private val sessionFactory: (
        assetPath: String,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
    ) -> VoiceInputSession = { assetPath, onPartialResult, onFinalResult ->
        AudioRecordSherpaOnnxVoiceInputSession(assetPath, onPartialResult, onFinalResult)
    },
) : VoiceInputRepository {
    private var activeSession: VoiceInputSession? = null

    override suspend fun start(
        assetPath: String,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
    ): VoiceInputState {
        val missingModelFile = REQUIRED_MODEL_FILES.firstOrNull { fileName ->
            !File(assetPath, fileName).isFile
        }
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
        val session = sessionFactory(assetPath, onPartialResult, onFinalResult)
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

    private companion object {
        const val SHERPA_ONNX_JNI_LIBRARY = "sherpa-onnx-jni"
        val REQUIRED_MODEL_FILES = listOf(
            "encoder-epoch-99-avg-1.onnx",
            "decoder-epoch-99-avg-1.onnx",
            "joiner-epoch-99-avg-1.onnx",
            "tokens.txt",
        )
    }
}

interface VoiceInputSession {
    fun start(): Result<Unit>
    fun stop()
}

private class AudioRecordSherpaOnnxVoiceInputSession(
    private val assetPath: String,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit,
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
            processSamples(nextRecognizer, nextAudioRecord)
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
        val stream = recognizer.createStream()
        try {
            val buffer = ShortArray((0.1 * sampleRateInHz).toInt())
            var committedText = ""
            while (recording) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                val samples = FloatArray(read) { index -> buffer[index] / 32768.0f }
                stream.acceptWaveform(samples, sampleRateInHz)
                while (recognizer.isReady(stream)) {
                    recognizer.decode(stream)
                }

                val partialText = recognizer.getResult(stream).text
                if (partialText.isNotBlank()) {
                    onPartialResult(joinAsrText(committedText, partialText))
                }

                if (recognizer.isEndpoint(stream)) {
                    if (partialText.isNotBlank()) {
                        committedText = joinAsrText(committedText, partialText)
                        onFinalResult(committedText)
                    }
                    recognizer.reset(stream)
                }
            }
        } finally {
            stream.release()
        }
    }

    private fun joinAsrText(prefix: String, suffix: String): String =
        when {
            prefix.isBlank() -> suffix
            suffix.isBlank() -> prefix
            else -> "$prefix $suffix"
        }

    private companion object {
        const val STOP_JOIN_TIMEOUT_MS = 500L
    }
}
