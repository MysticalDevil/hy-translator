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
        return modelDownloadStateFromRecord(
            status = this[MODEL_STATUS],
            modelKey = this[MODEL_KEY],
            path = this[MODEL_PATH],
            error = this[MODEL_ERROR],
            progressType = this[MODEL_PROGRESS_TYPE],
            downloaded = this[MODEL_DOWNLOADED],
            total = this[MODEL_TOTAL],
        )
    }

    private companion object {
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
        AiAsset.values()
            .asSequence()
            .map { asset -> preferences.toAiAssetDownloadState(asset) }
            .firstOrNull { it !is AiAssetDownloadState.Idle }
            ?: AiAssetDownloadState.Idle
    }

    fun state(asset: AiAsset): Flow<AiAssetDownloadState> = dataStore.data.map { preferences ->
        preferences.toAiAssetDownloadState(asset)
    }

    suspend fun setDownloading(asset: AiAsset, progress: DownloadProgress?) {
        dataStore.edit { preferences ->
            preferences[aiAssetStatusKey(asset)] = STATUS_DOWNLOADING
            preferences.remove(aiAssetPathKey(asset))
            preferences.remove(aiAssetErrorKey(asset))
            preferences.writeProgress(
                aiAssetProgressTypeKey(asset),
                aiAssetDownloadedKey(asset),
                aiAssetTotalKey(asset),
                progress,
            )
        }
    }

    suspend fun setCompleted(asset: AiAsset, path: String) {
        dataStore.edit { preferences ->
            preferences[aiAssetStatusKey(asset)] = STATUS_COMPLETED
            preferences[aiAssetPathKey(asset)] = path
            preferences.remove(aiAssetErrorKey(asset))
            preferences.clearProgress(
                aiAssetProgressTypeKey(asset),
                aiAssetDownloadedKey(asset),
                aiAssetTotalKey(asset),
            )
        }
    }

    suspend fun setError(asset: AiAsset, message: String) {
        dataStore.edit { preferences ->
            preferences[aiAssetStatusKey(asset)] = STATUS_ERROR
            preferences[aiAssetErrorKey(asset)] = message
            preferences.remove(aiAssetPathKey(asset))
            preferences.clearProgress(
                aiAssetProgressTypeKey(asset),
                aiAssetDownloadedKey(asset),
                aiAssetTotalKey(asset),
            )
        }
    }

    suspend fun setIdle() {
        dataStore.edit { preferences ->
            AiAsset.values().forEach { asset ->
                preferences.clearAsset(asset)
            }
        }
    }

    suspend fun setIdle(asset: AiAsset) {
        dataStore.edit { preferences ->
            preferences.clearAsset(asset)
        }
    }

    private fun Preferences.toAiAssetDownloadState(asset: AiAsset): AiAssetDownloadState {
        return aiAssetDownloadStateFromRecord(
            status = this[aiAssetStatusKey(asset)],
            assetName = asset.name,
            path = this[aiAssetPathKey(asset)],
            error = this[aiAssetErrorKey(asset)],
            progressType = this[aiAssetProgressTypeKey(asset)],
            downloaded = this[aiAssetDownloadedKey(asset)],
            total = this[aiAssetTotalKey(asset)],
        )
    }
}

internal const val STATUS_DOWNLOADING = "downloading"
internal const val STATUS_COMPLETED = "completed"
internal const val STATUS_ERROR = "error"
private const val PROGRESS_STARTED = "started"
private const val PROGRESS_DOWNLOADING = "downloading"

internal fun modelDownloadStateFromRecord(
    status: String?,
    modelKey: String?,
    path: String?,
    error: String?,
    progressType: String?,
    downloaded: Long?,
    total: Long?,
): ModelDownloadState {
    val model = modelKey?.let { key ->
        runCatching { ModelOptions.getByKey(key) }.getOrNull()
    } ?: return ModelDownloadState.Idle

    return when (status) {
        STATUS_DOWNLOADING -> ModelDownloadState.Downloading(
            model = model,
            progress = progressFromRecord(progressType, downloaded, total),
        )

        STATUS_COMPLETED -> ModelDownloadState.Completed(
            model = model,
            path = path ?: return ModelDownloadState.Idle,
        )

        STATUS_ERROR -> ModelDownloadState.Error(
            model = model,
            message = error ?: DEFAULT_DOWNLOAD_ERROR,
        )

        else -> ModelDownloadState.Idle
    }
}

internal fun aiAssetDownloadStateFromRecord(
    status: String?,
    assetName: String?,
    path: String?,
    error: String?,
    progressType: String?,
    downloaded: Long?,
    total: Long?,
): AiAssetDownloadState {
    val asset = assetName?.let { name ->
        runCatching { AiAsset.valueOf(name) }.getOrNull()
    } ?: return AiAssetDownloadState.Idle

    return when (status) {
        STATUS_DOWNLOADING -> AiAssetDownloadState.Downloading(
            asset = asset,
            progress = progressFromRecord(progressType, downloaded, total),
        )

        STATUS_COMPLETED -> AiAssetDownloadState.Completed(
            asset = asset,
            path = path ?: return AiAssetDownloadState.Idle,
        )

        STATUS_ERROR -> AiAssetDownloadState.Error(
            asset = asset,
            message = error ?: DEFAULT_DOWNLOAD_ERROR,
        )

        else -> AiAssetDownloadState.Idle
    }
}

internal fun progressFromRecord(
    progressType: String?,
    downloaded: Long?,
    total: Long?,
): DownloadProgress? {
    val progressTotal = total ?: return null
    val progressDownloaded = downloaded ?: 0L
    return when (progressType) {
        PROGRESS_STARTED -> DownloadProgress.Started(
            total = progressTotal,
            existing = progressDownloaded,
        )
        PROGRESS_DOWNLOADING -> DownloadProgress.Downloading(
            downloaded = progressDownloaded,
            total = progressTotal,
        )
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

private fun androidx.datastore.preferences.core.MutablePreferences.clearAsset(asset: AiAsset) {
    remove(aiAssetStatusKey(asset))
    remove(aiAssetPathKey(asset))
    remove(aiAssetErrorKey(asset))
    clearProgress(aiAssetProgressTypeKey(asset), aiAssetDownloadedKey(asset), aiAssetTotalKey(asset))
}

private fun aiAssetStatusKey(asset: AiAsset) =
    stringPreferencesKey("ai_asset_${asset.name}_status")

private fun aiAssetPathKey(asset: AiAsset) =
    stringPreferencesKey("ai_asset_${asset.name}_path")

private fun aiAssetErrorKey(asset: AiAsset) =
    stringPreferencesKey("ai_asset_${asset.name}_error")

private fun aiAssetProgressTypeKey(asset: AiAsset) =
    stringPreferencesKey("ai_asset_${asset.name}_progress_type")

private fun aiAssetDownloadedKey(asset: AiAsset) =
    longPreferencesKey("ai_asset_${asset.name}_downloaded")

private fun aiAssetTotalKey(asset: AiAsset) =
    longPreferencesKey("ai_asset_${asset.name}_total")

private fun androidx.datastore.preferences.core.MutablePreferences.clearProgress(
    typeKey: Preferences.Key<String>,
    downloadedKey: Preferences.Key<Long>,
    totalKey: Preferences.Key<Long>,
) {
    remove(typeKey)
    remove(downloadedKey)
    remove(totalKey)
}

private const val DEFAULT_DOWNLOAD_ERROR = "Download failed"
