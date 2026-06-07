package org.devil.hytranslator.service

import android.Manifest
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.devil.hytranslator.MainActivity
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
import java.io.InputStreamReader

@RunWith(AndroidJUnit4::class)
class DownloadTaskRemovedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val modelStateStore = ModelDownloadStateStore(context)
    private val aiAssetStateStore = AiAssetDownloadStateStore(context)

    @Before
    fun setUp() = runBlocking {
        grantNotificationPermission()
        DownloadServiceDependencies.reset()
        modelStateStore.setIdle()
        aiAssetStateStore.setIdle()
    }

    @After
    fun tearDown() = runBlocking {
        DownloadServiceDependencies.reset()
        modelStateStore.setIdle()
        aiAssetStateStore.setIdle()
    }

    @Test
    fun modelForegroundService_taskRemovedPersistsInterruptedError() = runBlocking {
        val model = ModelOptions.getByKey("Q4_K_M")
        DownloadServiceDependencies.modelRepositoryFactory = {
            BlockingModelRepository(model)
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            startForegroundService(
                Intent(context, ModelDownloadService::class.java)
                    .setAction(ModelDownloadService.ACTION_START)
                    .putExtra(ModelDownloadService.EXTRA_MODEL_KEY, model.key),
            )
            withTimeout(5_000) {
                modelStateStore.state.first { it is ModelDownloadState.Downloading }
            }

            removeAppTask()
        }

        val state = withTimeout(5_000) {
            modelStateStore.state.first { it is ModelDownloadState.Error }
        }

        assertEquals(
            ModelDownloadState.Error(
                model = model,
                message = DOWNLOAD_INTERRUPTED_MESSAGE,
                attempt = 1L,
                jobId = "model:Q4_K_M:1",
            ),
            state,
        )
    }

    @Test
    fun aiAssetForegroundService_taskRemovedPersistsInterruptedError() = runBlocking {
        DownloadServiceDependencies.aiAssetRepositoryFactory = {
            BlockingAiAssetRepository()
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            startForegroundService(
                Intent(context, AiAssetDownloadService::class.java)
                    .setAction(AiAssetDownloadService.ACTION_START)
                    .putExtra(AiAssetDownloadService.EXTRA_ASSET, AiAsset.OcrPpOcrV5Mobile.name),
            )
            withTimeout(5_000) {
                aiAssetStateStore.state(AiAsset.OcrPpOcrV5Mobile)
                    .first { it is AiAssetDownloadState.Downloading }
            }

            removeAppTask()
        }

        val state = withTimeout(5_000) {
            aiAssetStateStore.state(AiAsset.OcrPpOcrV5Mobile)
                .first { it is AiAssetDownloadState.Error }
        }

        assertEquals(
            AiAssetDownloadState.Error(
                asset = AiAsset.OcrPpOcrV5Mobile,
                message = DOWNLOAD_INTERRUPTED_MESSAGE,
                attempt = 1L,
                jobId = "ai:OcrPpOcrV5Mobile:1",
            ),
            state,
        )
    }

    private fun startForegroundService(intent: Intent) {
        ContextCompat.startForegroundService(context, intent)
    }

    private fun removeAppTask() {
        val taskId = currentAppTaskId()
        instrumentation.uiAutomation
            .executeShellCommand("am stack remove $taskId")
            .readTextAndClose()
    }

    private fun currentAppTaskId(): Int {
        val output = instrumentation.uiAutomation
            .executeShellCommand("am stack list")
            .readTextAndClose()
        val regex = Regex("""RootTask id=(\d+)[\s\S]*?${Regex.escape(context.packageName)}/""")
        val match = regex.find(output)
            ?: error("Could not find ${context.packageName} task in: $output")
        return match.groupValues[1].toInt()
    }

    private fun grantNotificationPermission() {
        instrumentation.uiAutomation
            .executeShellCommand(
                "pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}",
            )
            .readTextAndClose()
    }

    private fun ParcelFileDescriptor.readTextAndClose(): String =
        ParcelFileDescriptor.AutoCloseInputStream(this).use { input ->
            InputStreamReader(input).readText()
        }

    private class BlockingModelRepository(
        private var selectedModel: ModelOption,
    ) : ModelRepository {
        override fun allModels(): List<ModelOption> = ModelOptions.all

        override fun getModelPath(): String = "/tmp/${selectedModel.filename}"

        override fun isModelDownloaded(): Boolean = false

        override fun download(): Flow<DownloadProgress> = flow {
            emit(DownloadProgress.Started(total = 100L, existing = 0L))
            awaitCancellation()
        }

        override fun getRecommended(): ModelOption = selectedModel

        override fun getSelectedModel(): ModelOption = selectedModel

        override fun selectModel(model: ModelOption) {
            selectedModel = model
        }

        override fun clearAllModels() = Unit
    }

    private class BlockingAiAssetRepository : AiAssetRepository {
        private val states = AiAsset.values().associateWith {
            MutableStateFlow<AiAssetState>(AiAssetState.NotDownloaded)
        }

        override fun state(asset: AiAsset): MutableStateFlow<AiAssetState> =
            states.getValue(asset)

        override fun refresh(asset: AiAsset) = Unit

        override fun download(asset: AiAsset): Flow<DownloadProgress> = flow {
            emit(DownloadProgress.Started(total = 100L, existing = 0L))
            awaitCancellation()
        }

        override fun isReady(asset: AiAsset): Boolean = false

        override fun localPath(asset: AiAsset): String = "/tmp/${asset.name}"
    }

    private companion object {
        const val DOWNLOAD_INTERRUPTED_MESSAGE = "Download was interrupted"
    }
}
