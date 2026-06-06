package org.devil.hytranslator.domain.repository

import org.devil.hytranslator.domain.model.VoiceInputState

interface VoiceInputRepository {
    suspend fun start(assetPath: String): VoiceInputState

    fun stop()
}
