package org.devil.hytranslator.domain.usecase

import org.devil.hytranslator.domain.repository.ModelRepository
import org.devil.hytranslator.domain.repository.TranslatorRepository

class LoadModelUseCase(
    private val translatorRepository: TranslatorRepository,
    private val modelRepository: ModelRepository,
) {
    suspend operator fun invoke() {
        val path = modelRepository.getModelPath()
        translatorRepository.loadModel(path)
    }
}
