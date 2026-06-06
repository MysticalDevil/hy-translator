package org.devil.hytranslator.data.repository

import kotlinx.coroutines.test.runTest
import org.devil.hytranslator.domain.model.VoiceInputState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SherpaOnnxVoiceInputRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun start_whenModelFileIsMissing_returnsModelError() = runTest {
        val repository = SherpaOnnxVoiceInputRepository(
            nativeLoader = { error("native loader should not run") },
        )

        val state = repository.start(temporaryFolder.root.absolutePath)

        assertEquals(
            VoiceInputState.Error("Missing sherpa-onnx ASR file: encoder-epoch-99-avg-1.onnx"),
            state,
        )
    }

    @Test
    fun start_whenNativeRuntimeIsMissing_returnsNativeRuntimeError() = runTest {
        createRequiredModelFiles()
        val repository = SherpaOnnxVoiceInputRepository(
            nativeLoader = { throw UnsatisfiedLinkError("missing") },
        )

        val state = repository.start(temporaryFolder.root.absolutePath)

        assertEquals(
            VoiceInputState.Error(
                "sherpa-onnx native runtime is not installed. Run scripts/setup-sherpa-onnx-android.sh",
            ),
            state,
        )
    }

    @Test
    fun start_whenNativeRuntimeLoads_returnsAudioRecordIntegrationError() = runTest {
        createRequiredModelFiles()
        val repository = SherpaOnnxVoiceInputRepository(nativeLoader = {})

        val state = repository.start(temporaryFolder.root.absolutePath)

        assertEquals(
            VoiceInputState.Error("AudioRecord streaming decode is not integrated yet"),
            state,
        )
    }

    private fun createRequiredModelFiles() {
        listOf(
            "encoder-epoch-99-avg-1.onnx",
            "decoder-epoch-99-avg-1.onnx",
            "joiner-epoch-99-avg-1.onnx",
            "tokens.txt",
        ).forEach { name ->
            temporaryFolder.newFile(name).writeText("test")
        }
    }
}
