package org.devil.hytranslator.service

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.devil.hytranslator.data.ModelOptions
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.AiAssetDownloadState
import org.devil.hytranslator.domain.model.AiAssetState
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.domain.model.ModelDownloadState
import org.devil.hytranslator.domain.model.ModelOption
import org.devil.hytranslator.domain.repository.AiAssetRepository
import org.devil.hytranslator.domain.repository.ModelRepository
import org.devil.hytranslator.platform.download.AiAssetDownloadStateStore
import org.devil.hytranslator.platform.download.ModelDownloadStateStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadServiceRetryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val modelStateStore = ModelDownloadStateStore(context)
    private val aiAssetStateStore = AiAssetDownloadStateStore(context)

    @Before
    fun setUp() {
        DownloadServiceDependencies.reset()
    }

    @After
    fun tearDown() = runBlocking {
        DownloadServiceDependencies.reset()
        modelStateStore.setIdle()
        aiAssetStateStore.setIdle()
    }

    @Test
    fun modelStartAction_persistsCompletedState() = runBlocking {
        val model = ModelOptions.getByKey("Q4_K_M")
        modelStateStore.setIdle()
        DownloadServiceDependencies.modelRepositoryFactory = {
            FakeModelRepository(model, DownloadProgress.Completed(MODEL_PATH))
        }

        startForegroundService(
            Intent(context, ModelDownloadService::class.java)
                .setAction(ModelDownloadService.ACTION_START)
                .putExtra(ModelDownloadService.EXTRA_MODEL_KEY, model.key),
        )

        val state = withTimeout(5_000) {
            modelStateStore.state.first { it is ModelDownloadState.Completed }
        }

        assertEquals(
            ModelDownloadState.Completed(
                model = model,
                path = MODEL_PATH,
                attempt = 1L,
                jobId = "model:Q4_K_M:1",
            ),
            state,
        )
    }

    @Test
    fun modelStartAction_afterErrorPersistsNewRetryAttempt() = runBlocking {
        val model = ModelOptions.getByKey("Q4_K_M")
        modelStateStore.setError(model, "network failed")
        DownloadServiceDependencies.modelRepositoryFactory = {
            FakeModelRepository(model, DownloadProgress.Error(RETRY_FAILED_MESSAGE))
        }

        startForegroundService(
            Intent(context, ModelDownloadService::class.java)
                .setAction(ModelDownloadService.ACTION_START)
                .putExtra(ModelDownloadService.EXTRA_MODEL_KEY, model.key),
        )

        val state = withTimeout(5_000) {
            modelStateStore.state.first { candidate ->
                candidate is ModelDownloadState.Error &&
                    candidate.message == RETRY_FAILED_MESSAGE
            }
        }

        assertEquals(
            ModelDownloadState.Error(
                model = model,
                message = RETRY_FAILED_MESSAGE,
                attempt = 2L,
                jobId = "model:Q4_K_M:2",
            ),
            state,
        )
    }

    @Test
    fun aiAssetStartAction_persistsCompletedState() = runBlocking {
        aiAssetStateStore.setIdle(AiAsset.OcrPpOcrV5Mobile)
        DownloadServiceDependencies.aiAssetRepositoryFactory = {
            FakeAiAssetRepository(DownloadProgress.Completed(AI_ASSET_PATH))
        }

        startForegroundService(
            Intent(context, AiAssetDownloadService::class.java)
                .setAction(AiAssetDownloadService.ACTION_START)
                .putExtra(AiAssetDownloadService.EXTRA_ASSET, AiAsset.OcrPpOcrV5Mobile.name),
        )

        val state = withTimeout(5_000) {
            aiAssetStateStore.state(AiAsset.OcrPpOcrV5Mobile)
                .first { it is AiAssetDownloadState.Completed }
        }

        assertEquals(
            AiAssetDownloadState.Completed(
                asset = AiAsset.OcrPpOcrV5Mobile,
                path = AI_ASSET_PATH,
                attempt = 1L,
                jobId = "ai:OcrPpOcrV5Mobile:1",
            ),
            state,
        )
    }

    @Test
    fun aiAssetStartAction_afterErrorPersistsNewRetryAttempt() = runBlocking {
        aiAssetStateStore.setError(AiAsset.OcrPpOcrV5Mobile, "network failed")
        DownloadServiceDependencies.aiAssetRepositoryFactory = {
            FakeAiAssetRepository(DownloadProgress.Error(RETRY_FAILED_MESSAGE))
        }

        startForegroundService(
            Intent(context, AiAssetDownloadService::class.java)
                .setAction(AiAssetDownloadService.ACTION_START)
                .putExtra(AiAssetDownloadService.EXTRA_ASSET, AiAsset.OcrPpOcrV5Mobile.name),
        )

        val state = withTimeout(5_000) {
            aiAssetStateStore.state(AiAsset.OcrPpOcrV5Mobile).first { candidate ->
                candidate is AiAssetDownloadState.Error &&
                    candidate.message == RETRY_FAILED_MESSAGE
            }
        }

        assertEquals(
            AiAssetDownloadState.Error(
                asset = AiAsset.OcrPpOcrV5Mobile,
                message = RETRY_FAILED_MESSAGE,
                attempt = 2L,
                jobId = "ai:OcrPpOcrV5Mobile:2",
            ),
            state,
        )
    }

    private fun startForegroundService(intent: Intent) {
        ContextCompat.startForegroundService(context, intent)
    }

    private class FakeModelRepository(
        private var selectedModel: ModelOption,
        private val terminalProgress: DownloadProgress,
    ) : ModelRepository {
        override fun allModels(): List<ModelOption> = ModelOptions.all

        override fun getModelPath(): String = "/tmp/${selectedModel.filename}"

        override fun isModelDownloaded(): Boolean = false

        override fun download(): Flow<DownloadProgress> = flowOf(
            DownloadProgress.Started(total = 100L, existing = 0L),
            terminalProgress,
        )

        override fun getRecommended(): ModelOption = selectedModel

        override fun getSelectedModel(): ModelOption = selectedModel

        override fun selectModel(model: ModelOption) {
            selectedModel = model
        }

        override fun clearAllModels() = Unit
    }

    private class FakeAiAssetRepository(
        private val terminalProgress: DownloadProgress,
    ) : AiAssetRepository {
        private val states = AiAsset.values().associateWith {
            MutableStateFlow<AiAssetState>(AiAssetState.NotDownloaded)
        }

        override fun state(asset: AiAsset): MutableStateFlow<AiAssetState> =
            states.getValue(asset)

        override fun refresh(asset: AiAsset) = Unit

        override fun download(asset: AiAsset): Flow<DownloadProgress> = flowOf(
            DownloadProgress.Started(total = 100L, existing = 0L),
            terminalProgress,
        )

        override fun isReady(asset: AiAsset): Boolean = false

        override fun localPath(asset: AiAsset): String = "/tmp/${asset.name}"
    }

    private companion object {
        const val MODEL_PATH = "/tmp/model.gguf"
        const val AI_ASSET_PATH = "/tmp/ocr"
        const val RETRY_FAILED_MESSAGE = "retry failed"
    }
}
