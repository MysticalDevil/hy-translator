package org.devil.hytranslator.data.repository

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import org.devil.hytranslator.data.ModelOptions
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.domain.model.ModelOption
import org.devil.hytranslator.domain.repository.ModelRepository
import org.devil.hytranslator.service.ModelDownloader

class ModelRepositoryImpl(
    private val context: Context,
) : ModelRepository {

    private val prefs = context.getSharedPreferences("model_prefs", 0)
    private var selectedModel: ModelOption = loadSelectedModel()
    private var downloader: ModelDownloader = ModelDownloader(context, selectedModel.filename)

    private fun loadSelectedModel(): ModelOption {
        val savedKey = prefs.getString("model_key", null)
        val model = savedKey
            ?.let { key -> ModelOptions.all.firstOrNull { it.key == key } }
            ?: getRecommended()
        prefs.edit { putString("model_key", model.key) }
        return model
    }

    override fun getModelPath(): String = downloader.getModelPath()

    override fun isModelDownloaded(): Boolean = downloader.isModelDownloaded()

    override fun download(): Flow<DownloadProgress> = downloader.download()

    override fun getRecommended(): ModelOption = ModelOptions.recommend(context)

    override fun getSelectedModel(): ModelOption = selectedModel

    override fun selectModel(model: ModelOption) {
        selectedModel = model
        prefs.edit { putString("model_key", model.key) }
        downloader = ModelDownloader(context, model.filename)
    }

    override fun clearAllModels() {
        val modelDir = java.io.File(context.filesDir, "models")
        if (modelDir.isDirectory) {
            modelDir.listFiles()?.forEach { it.delete() }
        }
    }
}
