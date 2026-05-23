package org.devil.hytranslator.domain.usecase

import org.devil.hytranslator.domain.model.ModelOption
import org.devil.hytranslator.domain.repository.ModelRepository

class GetRecommendedModelUseCase(
    private val modelRepository: ModelRepository,
) {
    operator fun invoke(): ModelOption {
        return modelRepository.getRecommended()
    }
}
