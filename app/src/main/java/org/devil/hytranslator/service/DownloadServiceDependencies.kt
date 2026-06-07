package org.devil.hytranslator.service

import android.content.Context
import org.devil.hytranslator.data.repository.AiAssetRepositoryImpl
import org.devil.hytranslator.data.repository.ModelRepositoryImpl
import org.devil.hytranslator.domain.repository.AiAssetRepository
import org.devil.hytranslator.domain.repository.ModelRepository

internal object DownloadServiceDependencies {
    @Volatile
    var modelRepositoryFactory: (Context) -> ModelRepository = ::ModelRepositoryImpl

    @Volatile
    var aiAssetRepositoryFactory: (Context) -> AiAssetRepository = ::AiAssetRepositoryImpl

    fun reset() {
        modelRepositoryFactory = ::ModelRepositoryImpl
        aiAssetRepositoryFactory = ::AiAssetRepositoryImpl
    }
}
