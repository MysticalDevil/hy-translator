package org.devil.hytranslator.domain.repository

import org.devil.hytranslator.domain.model.VoiceInputState
import org.devil.hytranslator.domain.model.VoiceInputEvent

interface VoiceInputRepository {
    suspend fun start(
        assetPath: String,
        onEvent: (VoiceInputEvent) -> Unit,
    ): VoiceInputState

    fun stop()
}
