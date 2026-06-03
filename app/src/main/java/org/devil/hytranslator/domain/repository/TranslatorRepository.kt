package org.devil.hytranslator.domain.repository

import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.devil.hytranslator.domain.model.Language

interface TranslatorRepository {
    val state: StateFlow<InferenceEngine.State>

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
