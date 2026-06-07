package org.devil.hytranslator.service

import android.Manifest
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStreamReader

@RunWith(AndroidJUnit4::class)
class DownloadUidtJobSchedulerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val modelStateStore = ModelDownloadStateStore(context)
    private val aiAssetStateStore = AiAssetDownloadStateStore(context)

    @Before
    fun setUp() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        grantNotificationPermission()
        DownloadServiceDependencies.reset()
    }

    @After
    fun tearDown() = runBlocking {
        DownloadServiceDependencies.reset()
        modelStateStore.setIdle()
        aiAssetStateStore.setIdle()
    }

    @Test
    fun uidtModelJob_runsThroughJobSchedulerAndPersistsCompletedState() = runBlocking {
        val model = ModelOptions.getByKey("Q4_K_M")
        DownloadServiceDependencies.modelRepositoryFactory = {
            FakeModelRepository(model, DownloadProgress.Completed(MODEL_PATH))
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            ModelDownloadUidtScheduler.schedule(context, model)
            runScheduledJob(MODEL_DOWNLOAD_UIDT_JOB_ID)
        }

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
    fun uidtAiAssetJob_runsThroughJobSchedulerAndPersistsCompletedState() = runBlocking {
        DownloadServiceDependencies.aiAssetRepositoryFactory = {
            FakeAiAssetRepository(DownloadProgress.Completed(AI_ASSET_PATH))
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            AiAssetDownloadUidtScheduler.schedule(context, AiAsset.OcrPpOcrV5Mobile)
            runScheduledJob(OCR_DOWNLOAD_UIDT_JOB_ID)
        }

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

    private fun runScheduledJob(jobId: Int) {
        val output = instrumentation.uiAutomation
            .executeShellCommand("cmd jobscheduler run -f ${context.packageName} $jobId")
            .readTextAndClose()

        check(
            "Running job" in output ||
                "Started job" in output ||
                output.isBlank(),
        ) {
            "Unexpected jobscheduler output: $output"
        }
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
        const val MODEL_PATH = "/tmp/model-uidt.gguf"
        const val AI_ASSET_PATH = "/tmp/ocr-uidt"
    }
}
