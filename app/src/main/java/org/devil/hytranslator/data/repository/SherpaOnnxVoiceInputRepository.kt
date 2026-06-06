package org.devil.hytranslator.data.repository

import org.devil.hytranslator.domain.model.VoiceInputState
import org.devil.hytranslator.domain.repository.VoiceInputRepository
import java.io.File

class SherpaOnnxVoiceInputRepository(
    private val nativeLoader: (String) -> Unit = { libraryName ->
        System.loadLibrary(libraryName)
    },
) : VoiceInputRepository {
    override suspend fun start(assetPath: String): VoiceInputState {
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

        return VoiceInputState.Error("AudioRecord streaming decode is not integrated yet")
    }

    override fun stop() = Unit

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
