package org.devil.hytranslator.domain.usecase

import kotlinx.coroutines.flow.Flow
import org.devil.hytranslator.domain.model.Language
import org.devil.hytranslator.domain.repository.TranslatorRepository

class TranslateUseCase(
    private val translatorRepository: TranslatorRepository,
) {
    operator fun invoke(
        text: String,
        sourceLang: Language,
        targetLang: Language,
        maxTokens: Int = 4096,
    ): Flow<String> {
        return translatorRepository.translate(text, sourceLang, targetLang, maxTokens)
    }
}
