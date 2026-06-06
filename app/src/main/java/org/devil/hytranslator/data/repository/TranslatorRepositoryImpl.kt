package org.devil.hytranslator.data.repository

import android.content.Context
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.devil.hytranslator.domain.model.Language
import org.devil.hytranslator.domain.model.TranslationEngineState
import org.devil.hytranslator.domain.repository.TranslatorRepository
import org.devil.hytranslator.service.TranslatorEngine

class TranslatorRepositoryImpl(context: Context) : TranslatorRepository {

    private val engine = TranslatorEngine(context)
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val state: StateFlow<TranslationEngineState> = engine.state
        .map(::mapEngineState)
        .stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = mapEngineState(engine.state.value),
        )

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

    override suspend fun unloadModel() {
        engine.unloadModel()
    }

    override suspend fun destroy() {
        try {
            engine.destroy()
        } finally {
            repositoryScope.cancel()
        }
    }

    private fun mapEngineState(state: InferenceEngine.State): TranslationEngineState =
        when (state) {
            is InferenceEngine.State.Initializing -> TranslationEngineState.Loading
            is InferenceEngine.State.LoadingModel -> TranslationEngineState.Loading
            is InferenceEngine.State.UnloadingModel -> TranslationEngineState.Loading
            is InferenceEngine.State.ModelReady -> TranslationEngineState.Ready
            is InferenceEngine.State.Generating -> TranslationEngineState.Generating
            is InferenceEngine.State.Error -> TranslationEngineState.Error(
                state.exception.message ?: state.exception.javaClass.simpleName,
            )
            else -> TranslationEngineState.Idle
        }
}
