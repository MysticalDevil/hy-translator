package org.devil.hytranslator.domain.repository

import kotlinx.coroutines.flow.Flow
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.domain.model.ModelOption

interface ModelRepository {
    fun allModels(): List<ModelOption>

    fun getModelPath(): String

    fun isModelDownloaded(): Boolean

    fun download(): Flow<DownloadProgress>

    fun getRecommended(): ModelOption

    fun getSelectedModel(): ModelOption

    fun selectModel(model: ModelOption)

    fun clearAllModels()
}
