package org.devil.hytranslator.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.devil.hytranslator.data.ModelOptions
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.domain.model.ModelOption
import org.devil.hytranslator.domain.repository.ModelRepository
import org.devil.hytranslator.service.ModelDownloader

private val Context.modelPreferencesDataStore by preferencesDataStore(name = "model_prefs")

class ModelRepositoryImpl(
    private val context: Context,
) : ModelRepository {

    private var selectedModel: ModelOption = loadSelectedModel()
    private var downloader: ModelDownloader = ModelDownloader(context, selectedModel.filename)
    private val bundledModelInstaller = BundledModelInstaller(context)

    override fun allModels(): List<ModelOption> = ModelOptions.all

    private fun loadSelectedModel(): ModelOption {
        val savedKey = runBlocking(Dispatchers.IO) {
            context.modelPreferencesDataStore.data
                .map { preferences -> preferences[MODEL_KEY] }
                .first()
        }
        val model = savedKey
            ?.let { key -> ModelOptions.all.firstOrNull { it.key == key } }
            ?: getRecommended()
        persistSelectedModel(model)
        return model
    }

    override fun getModelPath(): String = downloader.getModelPath()

    override fun isModelDownloaded(): Boolean {
        bundledModelInstaller.installIfPresent(selectedModel)
        return downloader.isModelDownloaded()
    }

    override fun download(): Flow<DownloadProgress> {
        bundledModelInstaller.installIfPresent(selectedModel)
        return if (downloader.isModelDownloaded()) {
            kotlinx.coroutines.flow.flowOf(DownloadProgress.Completed(downloader.getModelPath()))
        } else {
            downloader.download()
        }
    }

    override fun getRecommended(): ModelOption = ModelOptions.recommend(context)

    override fun getSelectedModel(): ModelOption = selectedModel

    override fun selectModel(model: ModelOption) {
        selectedModel = model
        persistSelectedModel(model)
        downloader = ModelDownloader(context, model.filename)
    }

    override fun clearAllModels() {
        val modelDir = java.io.File(context.filesDir, "models")
        if (modelDir.isDirectory) {
            modelDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun persistSelectedModel(model: ModelOption) {
        runBlocking(Dispatchers.IO) {
            context.modelPreferencesDataStore.edit { preferences ->
                preferences[MODEL_KEY] = model.key
            }
        }
    }

    private companion object {
        val MODEL_KEY = stringPreferencesKey("model_key")
    }
}
