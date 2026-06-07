package org.devil.hytranslator.domain.repository

import org.devil.hytranslator.domain.model.VoiceInputState

interface VoiceInputRepository {
    suspend fun start(
        assetPath: String,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit,
    ): VoiceInputState

    fun stop()
}
