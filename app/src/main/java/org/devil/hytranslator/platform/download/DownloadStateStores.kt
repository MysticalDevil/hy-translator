package org.devil.hytranslator.platform.download

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.devil.hytranslator.data.ModelOptions
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.AiAssetDownloadState
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.domain.model.ModelDownloadState
import org.devil.hytranslator.domain.model.ModelOption

private val Context.downloadStateDataStore by preferencesDataStore(name = "download_state")

class ModelDownloadStateStore(
    context: Context,
) {
    private val dataStore = context.applicationContext.downloadStateDataStore

    val state: Flow<ModelDownloadState> = dataStore.data.map { preferences ->
        preferences.toModelDownloadState()
    }

    suspend fun setDownloading(model: ModelOption, progress: DownloadProgress?) {
        dataStore.edit { preferences ->
            preferences[MODEL_STATUS] = STATUS_DOWNLOADING
            preferences[MODEL_KEY] = model.key
            preferences.remove(MODEL_PATH)
            preferences.remove(MODEL_ERROR)
            preferences.writeProgress(MODEL_PROGRESS_TYPE, MODEL_DOWNLOADED, MODEL_TOTAL, progress)
        }
    }

    suspend fun setCompleted(model: ModelOption, path: String) {
        dataStore.edit { preferences ->
            preferences[MODEL_STATUS] = STATUS_COMPLETED
            preferences[MODEL_KEY] = model.key
            preferences[MODEL_PATH] = path
            preferences.remove(MODEL_ERROR)
            preferences.clearProgress(MODEL_PROGRESS_TYPE, MODEL_DOWNLOADED, MODEL_TOTAL)
        }
    }

    suspend fun setError(model: ModelOption, message: String) {
        dataStore.edit { preferences ->
            preferences[MODEL_STATUS] = STATUS_ERROR
            preferences[MODEL_KEY] = model.key
            preferences[MODEL_ERROR] = message
            preferences.remove(MODEL_PATH)
            preferences.clearProgress(MODEL_PROGRESS_TYPE, MODEL_DOWNLOADED, MODEL_TOTAL)
        }
    }

    suspend fun setIdle() {
        dataStore.edit { preferences ->
            preferences.remove(MODEL_STATUS)
            preferences.remove(MODEL_KEY)
            preferences.remove(MODEL_PATH)
            preferences.remove(MODEL_ERROR)
            preferences.clearProgress(MODEL_PROGRESS_TYPE, MODEL_DOWNLOADED, MODEL_TOTAL)
        }
    }

    private fun Preferences.toModelDownloadState(): ModelDownloadState {
        val model = this[MODEL_KEY]?.let { key ->
            runCatching { ModelOptions.getByKey(key) }.getOrNull()
        } ?: return ModelDownloadState.Idle

        return when (this[MODEL_STATUS]) {
            STATUS_DOWNLOADING -> ModelDownloadState.Downloading(
                model = model,
                progress = readProgress(MODEL_PROGRESS_TYPE, MODEL_DOWNLOADED, MODEL_TOTAL),
            )

            STATUS_COMPLETED -> ModelDownloadState.Completed(
                model = model,
                path = this[MODEL_PATH] ?: return ModelDownloadState.Idle,
            )

            STATUS_ERROR -> ModelDownloadState.Error(
                model = model,
                message = this[MODEL_ERROR] ?: "Download failed",
            )

            else -> ModelDownloadState.Idle
        }
    }

    private companion object {
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_ERROR = "error"

        val MODEL_STATUS = stringPreferencesKey("model_status")
        val MODEL_KEY = stringPreferencesKey("model_key")
        val MODEL_PATH = stringPreferencesKey("model_path")
        val MODEL_ERROR = stringPreferencesKey("model_error")
        val MODEL_PROGRESS_TYPE = stringPreferencesKey("model_progress_type")
        val MODEL_DOWNLOADED = longPreferencesKey("model_downloaded")
        val MODEL_TOTAL = longPreferencesKey("model_total")
    }
}

class AiAssetDownloadStateStore(
    context: Context,
) {
    private val dataStore = context.applicationContext.downloadStateDataStore

    val state: Flow<AiAssetDownloadState> = dataStore.data.map { preferences ->
        preferences.toAiAssetDownloadState()
    }

    suspend fun setDownloading(asset: AiAsset, progress: DownloadProgress?) {
        dataStore.edit { preferences ->
            preferences[AI_ASSET_STATUS] = STATUS_DOWNLOADING
            preferences[AI_ASSET_NAME] = asset.name
            preferences.remove(AI_ASSET_PATH)
            preferences.remove(AI_ASSET_ERROR)
            preferences.writeProgress(AI_ASSET_PROGRESS_TYPE, AI_ASSET_DOWNLOADED, AI_ASSET_TOTAL, progress)
        }
    }

    suspend fun setCompleted(asset: AiAsset, path: String) {
        dataStore.edit { preferences ->
            preferences[AI_ASSET_STATUS] = STATUS_COMPLETED
            preferences[AI_ASSET_NAME] = asset.name
            preferences[AI_ASSET_PATH] = path
            preferences.remove(AI_ASSET_ERROR)
            preferences.clearProgress(AI_ASSET_PROGRESS_TYPE, AI_ASSET_DOWNLOADED, AI_ASSET_TOTAL)
        }
    }

    suspend fun setError(asset: AiAsset, message: String) {
        dataStore.edit { preferences ->
            preferences[AI_ASSET_STATUS] = STATUS_ERROR
            preferences[AI_ASSET_NAME] = asset.name
            preferences[AI_ASSET_ERROR] = message
            preferences.remove(AI_ASSET_PATH)
            preferences.clearProgress(AI_ASSET_PROGRESS_TYPE, AI_ASSET_DOWNLOADED, AI_ASSET_TOTAL)
        }
    }

    suspend fun setIdle() {
        dataStore.edit { preferences ->
            preferences.remove(AI_ASSET_STATUS)
            preferences.remove(AI_ASSET_NAME)
            preferences.remove(AI_ASSET_PATH)
            preferences.remove(AI_ASSET_ERROR)
            preferences.clearProgress(AI_ASSET_PROGRESS_TYPE, AI_ASSET_DOWNLOADED, AI_ASSET_TOTAL)
        }
    }

    private fun Preferences.toAiAssetDownloadState(): AiAssetDownloadState {
        val asset = this[AI_ASSET_NAME]?.let { name ->
            runCatching { AiAsset.valueOf(name) }.getOrNull()
        } ?: return AiAssetDownloadState.Idle

        return when (this[AI_ASSET_STATUS]) {
            STATUS_DOWNLOADING -> AiAssetDownloadState.Downloading(
                asset = asset,
                progress = readProgress(AI_ASSET_PROGRESS_TYPE, AI_ASSET_DOWNLOADED, AI_ASSET_TOTAL),
            )

            STATUS_COMPLETED -> AiAssetDownloadState.Completed(
                asset = asset,
                path = this[AI_ASSET_PATH] ?: return AiAssetDownloadState.Idle,
            )

            STATUS_ERROR -> AiAssetDownloadState.Error(
                asset = asset,
                message = this[AI_ASSET_ERROR] ?: "Download failed",
            )

            else -> AiAssetDownloadState.Idle
        }
    }

    private companion object {
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_ERROR = "error"

        val AI_ASSET_STATUS = stringPreferencesKey("ai_asset_status")
        val AI_ASSET_NAME = stringPreferencesKey("ai_asset_name")
        val AI_ASSET_PATH = stringPreferencesKey("ai_asset_path")
        val AI_ASSET_ERROR = stringPreferencesKey("ai_asset_error")
        val AI_ASSET_PROGRESS_TYPE = stringPreferencesKey("ai_asset_progress_type")
        val AI_ASSET_DOWNLOADED = longPreferencesKey("ai_asset_downloaded")
        val AI_ASSET_TOTAL = longPreferencesKey("ai_asset_total")
    }
}

private const val PROGRESS_STARTED = "started"
private const val PROGRESS_DOWNLOADING = "downloading"

private fun Preferences.readProgress(
    typeKey: Preferences.Key<String>,
    downloadedKey: Preferences.Key<Long>,
    totalKey: Preferences.Key<Long>,
): DownloadProgress? {
    val total = this[totalKey] ?: return null
    val downloaded = this[downloadedKey] ?: 0L
    return when (this[typeKey]) {
        PROGRESS_STARTED -> DownloadProgress.Started(total = total, existing = downloaded)
        PROGRESS_DOWNLOADING -> DownloadProgress.Downloading(downloaded = downloaded, total = total)
        else -> null
    }
}

private fun androidx.datastore.preferences.core.MutablePreferences.writeProgress(
    typeKey: Preferences.Key<String>,
    downloadedKey: Preferences.Key<Long>,
    totalKey: Preferences.Key<Long>,
    progress: DownloadProgress?,
) {
    when (progress) {
        is DownloadProgress.Started -> {
            this[typeKey] = PROGRESS_STARTED
            this[downloadedKey] = progress.existing
            this[totalKey] = progress.total
        }

        is DownloadProgress.Downloading -> {
            this[typeKey] = PROGRESS_DOWNLOADING
            this[downloadedKey] = progress.downloaded
            this[totalKey] = progress.total
        }

        is DownloadProgress.Completed,
        is DownloadProgress.Error,
        null,
        -> clearProgress(typeKey, downloadedKey, totalKey)
    }
}

private fun androidx.datastore.preferences.core.MutablePreferences.clearProgress(
    typeKey: Preferences.Key<String>,
    downloadedKey: Preferences.Key<Long>,
    totalKey: Preferences.Key<Long>,
) {
    remove(typeKey)
    remove(downloadedKey)
    remove(totalKey)
}
