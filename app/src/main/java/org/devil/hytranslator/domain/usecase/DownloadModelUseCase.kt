package org.devil.hytranslator.domain.usecase

import kotlinx.coroutines.flow.Flow
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.domain.repository.ModelRepository

class DownloadModelUseCase(
    private val modelRepository: ModelRepository,
) {
    operator fun invoke(): Flow<DownloadProgress> {
        return modelRepository.download()
    }
}
