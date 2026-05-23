package org.devil.hytranslator.data.repository

import android.content.Context
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.devil.hytranslator.domain.model.Language
import org.devil.hytranslator.domain.repository.TranslatorRepository
import org.devil.hytranslator.service.TranslatorEngine

class TranslatorRepositoryImpl(context: Context) : TranslatorRepository {

    private val engine = TranslatorEngine(context)

    override val state: StateFlow<InferenceEngine.State>
        get() = engine.state

    override fun isModelReady(): Boolean = engine.isModelReady()

    override suspend fun loadModel(path: String) {
        engine.loadModel(path)
    }

    override fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language,
        maxTokens: Int,
    ): Flow<String> {
        return engine.translate(text, sourceLang, targetLang, maxTokens)
    }

    override fun unloadModel() {
        engine.unloadModel()
    }

    override fun destroy() {
        engine.destroy()
    }
}
