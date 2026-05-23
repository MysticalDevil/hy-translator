package org.devil.hytranslator.service

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.flow.Flow
import org.devil.hytranslator.domain.model.Language

class TranslatorEngine(context: Context) {

    private val engine: InferenceEngine = AiChat.getInferenceEngine(context.applicationContext)

    val state get() = engine.state

    fun isModelReady(): Boolean {
        val s = engine.state.value
        return s is InferenceEngine.State.ModelReady || s is InferenceEngine.State.Generating
    }

    suspend fun loadModel(path: String) {
        engine.loadModel(path)
    }

    fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language,
        maxTokens: Int = 4096,
    ): Flow<String> {
        val prompt = buildPrompt(sourceLang, targetLang, text)
        return engine.sendUserPrompt(prompt, predictLength = maxTokens)
    }

    fun cancel() {
        engine.cleanUp()
    }

    fun destroy() {
        engine.destroy()
    }

    companion object {
        fun buildPrompt(sourceLang: Language, targetLang: Language, text: String): String {
            return if (sourceLang.code == "auto") {
                "Translate the following text into ${targetLang.englishName}. " +
                    "Note that you must ONLY output the translated result without any additional explanation:\n\n$text"
            } else {
                "Translate the following ${sourceLang.englishName} text into ${targetLang.englishName}. " +
                    "Note that you must ONLY output the translated result without any additional explanation:\n\n$text"
            }
        }
    }
}
