package org.devil.hytranslator.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.devil.hytranslator.domain.model.Language
import org.devil.hytranslator.domain.model.TranslationEngineState

interface TranslatorRepository {
    val state: StateFlow<TranslationEngineState>

    fun isModelReady(): Boolean

    suspend fun loadModel(path: String)

    fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language,
        maxTokens: Int = 4096,
    ): Flow<String>

    suspend fun unloadModel()

    suspend fun destroy()
}
