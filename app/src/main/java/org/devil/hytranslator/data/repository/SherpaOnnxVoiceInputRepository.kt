package org.devil.hytranslator.data.repository

import org.devil.hytranslator.domain.model.VoiceInputState
import org.devil.hytranslator.domain.repository.VoiceInputRepository

class SherpaOnnxVoiceInputRepository : VoiceInputRepository {
    override suspend fun start(assetPath: String): VoiceInputState =
        VoiceInputState.Error("sherpa-onnx streaming runtime is not integrated yet")

    override fun stop() = Unit
}
