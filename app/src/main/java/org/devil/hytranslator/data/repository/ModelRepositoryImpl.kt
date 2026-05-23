package org.devil.hytranslator.data.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow
import org.devil.hytranslator.data.ModelOptions
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.domain.model.ModelOption
import org.devil.hytranslator.domain.repository.ModelRepository
import org.devil.hytranslator.service.ModelDownloader

class ModelRepositoryImpl(
    private val context: Context,
) : ModelRepository {

    private var downloader: ModelDownloader? = null

    fun setModelFilename(filename: String) {
        downloader = ModelDownloader(context, filename)
    }

    private fun requireDownloader(): ModelDownloader =
        downloader ?: error("Model filename not set. Call setModelFilename() first.")

    override fun getModelPath(): String = requireDownloader().getModelPath()

    override fun isModelDownloaded(): Boolean = requireDownloader().isModelDownloaded()

    override fun download(): Flow<DownloadProgress> = requireDownloader().download()

    override fun getRecommended(): ModelOption = ModelOptions.recommend(context)

    override fun clearAllModels() {
        val modelDir = java.io.File(context.filesDir, "models")
        if (modelDir.isDirectory) {
            modelDir.listFiles()?.forEach { it.delete() }
        }
    }
}
