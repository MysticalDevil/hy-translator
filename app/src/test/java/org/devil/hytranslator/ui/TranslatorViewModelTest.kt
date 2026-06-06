package org.devil.hytranslator.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.devil.hytranslator.MainDispatcherRule
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.AiAssetDownloadState
import org.devil.hytranslator.domain.model.AiAssetState
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.domain.model.Language
import org.devil.hytranslator.domain.model.ModelDownloadState
import org.devil.hytranslator.domain.model.ModelOption
import org.devil.hytranslator.domain.model.ModelStatus
import org.devil.hytranslator.domain.model.TranslationEngineState
import org.devil.hytranslator.domain.model.VoiceInputState
import org.devil.hytranslator.domain.repository.AiAssetRepository
import org.devil.hytranslator.domain.repository.LanguageRepository
import org.devil.hytranslator.domain.repository.ModelRepository
import org.devil.hytranslator.domain.repository.TranslatorRepository
import org.devil.hytranslator.domain.repository.VoiceInputRepository
import org.devil.hytranslator.service.AiAssetDownloadActions
import org.devil.hytranslator.service.ModelDownloadActions
import org.devil.hytranslator.service.ModelDownloadNotifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranslatorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun translate_appendsTokensAndResetsLoadingState() = runTest {
        val translatorRepository = FakeTranslatorRepository(
            isReady = true,
            tokens = listOf("Bon", "jour"),
        )
        val viewModel = createViewModel(translatorRepository = translatorRepository)

        viewModel.onInputTextChange("hello")
        viewModel.onTranslate()
        advanceUntilIdle()

        assertEquals("Bonjour", viewModel.outputText.value)
        assertEquals("Bonjour", viewModel.uiState.value.outputText)
        assertFalse(viewModel.isTranslating.value)
        assertFalse(viewModel.uiState.value.isTranslating)
        assertEquals("hello", translatorRepository.lastText)
    }

    @Test
    fun translate_whenModelIsNotReady_doesNothing() = runTest {
        val translatorRepository = FakeTranslatorRepository(isReady = false)
        val viewModel = createViewModel(translatorRepository = translatorRepository)

        viewModel.onInputTextChange("hello")
        viewModel.onTranslate()
        advanceUntilIdle()

        assertEquals("", viewModel.outputText.value)
        assertEquals("", viewModel.uiState.value.outputText)
        assertFalse(viewModel.isTranslating.value)
        assertEquals(0, translatorRepository.translateCalls)
    }

    @Test
    fun onEvent_routesInputAndTranslateEvents() = runTest {
        val translatorRepository = FakeTranslatorRepository(
            isReady = true,
            tokens = listOf("Salut"),
        )
        val viewModel = createViewModel(translatorRepository = translatorRepository)

        viewModel.onEvent(TranslatorEvent.InputChanged("hello"))
        viewModel.onEvent(TranslatorEvent.Translate)
        advanceUntilIdle()

        assertEquals("hello", viewModel.uiState.value.inputText)
        assertEquals("Salut", viewModel.uiState.value.outputText)
        assertEquals(1, translatorRepository.translateCalls)
    }

    @Test
    fun liveTranslate_isDisabledByDefaultAndInputDoesNotTranslate() = runTest {
        val translatorRepository = FakeTranslatorRepository(
            isReady = true,
            tokens = listOf("Bonjour"),
        )
        val modelRepository = FakeModelRepository().apply { downloaded = true }
        val viewModel = createViewModel(
            translatorRepository = translatorRepository,
            modelRepository = modelRepository,
        )

        viewModel.initialize()
        advanceUntilIdle()
        viewModel.onEvent(TranslatorEvent.InputChanged("hello"))
        advanceTimeBy(1_000)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLiveTranslateEnabled)
        assertEquals(0, translatorRepository.translateCalls)
        assertEquals("", viewModel.uiState.value.outputText)
    }

    @Test
    fun liveTranslate_whenEnabled_translatesAfterDebounce() = runTest {
        val translatorRepository = FakeTranslatorRepository(
            isReady = true,
            tokens = listOf("Bonjour"),
        )
        val modelRepository = FakeModelRepository().apply { downloaded = true }
        val viewModel = createViewModel(
            translatorRepository = translatorRepository,
            modelRepository = modelRepository,
        )

        viewModel.initialize()
        advanceUntilIdle()
        viewModel.onEvent(TranslatorEvent.LiveTranslateToggled(true))
        viewModel.onEvent(TranslatorEvent.InputChanged("hello"))
        advanceTimeBy(599)

        assertEquals(0, translatorRepository.translateCalls)

        advanceTimeBy(1)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLiveTranslateEnabled)
        assertEquals("hello", translatorRepository.lastText)
        assertEquals("Bonjour", viewModel.uiState.value.outputText)
    }

    @Test
    fun liveTranslate_continuousInputTranslatesOnlyLatestText() = runTest {
        val translatorRepository = FakeTranslatorRepository(
            isReady = true,
            tokens = listOf("Bonjour"),
        )
        val modelRepository = FakeModelRepository().apply { downloaded = true }
        val viewModel = createViewModel(
            translatorRepository = translatorRepository,
            modelRepository = modelRepository,
        )

        viewModel.initialize()
        advanceUntilIdle()
        viewModel.onEvent(TranslatorEvent.LiveTranslateToggled(true))
        viewModel.onEvent(TranslatorEvent.InputChanged("h"))
        advanceTimeBy(300)
        viewModel.onEvent(TranslatorEvent.InputChanged("hello"))
        advanceTimeBy(600)
        advanceUntilIdle()

        assertEquals(1, translatorRepository.translateCalls)
        assertEquals("hello", translatorRepository.lastText)
        assertEquals("Bonjour", viewModel.uiState.value.outputText)
    }

    @Test
    fun liveTranslate_whenModelIsNotReady_doesNotTranslate() = runTest {
        val translatorRepository = FakeTranslatorRepository(isReady = false)
        val viewModel = createViewModel(translatorRepository = translatorRepository)

        viewModel.onEvent(TranslatorEvent.LiveTranslateToggled(true))
        viewModel.onEvent(TranslatorEvent.InputChanged("hello"))
        advanceTimeBy(1_000)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLiveTranslateEnabled)
        assertEquals(0, translatorRepository.translateCalls)
        assertEquals("", viewModel.uiState.value.outputText)
    }

    @Test
    fun voiceInput_whenAsrAssetIsMissing_requestsAsrModel() = runTest {
        val aiAssetRepository = FakeAiAssetRepository()
        val viewModel = createViewModel(aiAssetRepository = aiAssetRepository)

        viewModel.initialize()
        advanceUntilIdle()
        viewModel.onEvent(TranslatorEvent.VoiceInputToggled(true))

        assertSame(AiAssetState.NotDownloaded, viewModel.uiState.value.asrAssetState)
        assertSame(VoiceInputState.NeedsAsrModel, viewModel.uiState.value.voiceInputState)
    }

    @Test
    fun voiceInput_whenAsrAssetIsReady_entersListeningState() = runTest {
        val aiAssetRepository = FakeAiAssetRepository().apply {
            asrState.value = AiAssetState.Ready
        }
        val voiceInputRepository = FakeVoiceInputRepository()
        val viewModel = createViewModel(
            aiAssetRepository = aiAssetRepository,
            voiceInputRepository = voiceInputRepository,
        )

        viewModel.initialize()
        advanceUntilIdle()
        viewModel.onEvent(TranslatorEvent.VoiceInputToggled(true))
        advanceUntilIdle()

        assertEquals("/ai-assets/AsrStreamingZipformer", voiceInputRepository.startedAssetPath)
        assertSame(AiAssetState.Ready, viewModel.uiState.value.asrAssetState)
        assertSame(VoiceInputState.Listening, viewModel.uiState.value.voiceInputState)
    }

    @Test
    fun voiceInput_whenDisabled_stopsRuntimeAndReturnsIdle() = runTest {
        val aiAssetRepository = FakeAiAssetRepository().apply {
            asrState.value = AiAssetState.Ready
        }
        val voiceInputRepository = FakeVoiceInputRepository()
        val viewModel = createViewModel(
            aiAssetRepository = aiAssetRepository,
            voiceInputRepository = voiceInputRepository,
        )

        viewModel.initialize()
        advanceUntilIdle()
        viewModel.onEvent(TranslatorEvent.VoiceInputToggled(true))
        advanceUntilIdle()
        viewModel.onEvent(TranslatorEvent.VoiceInputToggled(false))

        assertTrue(voiceInputRepository.stopped)
        assertSame(VoiceInputState.Idle, viewModel.uiState.value.voiceInputState)
    }

    @Test
    fun voiceInputPermissionDenied_stopsRuntimeAndShowsError() = runTest {
        val voiceInputRepository = FakeVoiceInputRepository()
        val viewModel = createViewModel(voiceInputRepository = voiceInputRepository)

        viewModel.onEvent(TranslatorEvent.VoiceInputPermissionDenied("permission denied"))

        assertTrue(voiceInputRepository.stopped)
        assertEquals(
            VoiceInputState.Error("permission denied"),
            viewModel.uiState.value.voiceInputState,
        )
    }

    @Test
    fun refreshAiAsset_routesToAssetRepository() = runTest {
        val aiAssetRepository = FakeAiAssetRepository()
        val viewModel = createViewModel(aiAssetRepository = aiAssetRepository)

        viewModel.onEvent(TranslatorEvent.RefreshAiAsset(AiAsset.OcrPpOcrV5Mobile))

        assertEquals(AiAsset.OcrPpOcrV5Mobile, aiAssetRepository.refreshedAsset)
    }

    @Test
    fun downloadAiAsset_startsServiceAndUpdatesStateFromService() = runTest {
        val aiAssetRepository = FakeAiAssetRepository()
        val aiAssetDownloadActions = FakeAiAssetDownloadActions()
        val viewModel = createViewModel(
            aiAssetRepository = aiAssetRepository,
            aiAssetDownloadActions = aiAssetDownloadActions,
        )

        viewModel.initialize()
        advanceUntilIdle()
        viewModel.onEvent(TranslatorEvent.DownloadAiAsset(AiAsset.AsrStreamingZipformer))
        advanceUntilIdle()

        assertEquals(AiAsset.AsrStreamingZipformer, aiAssetDownloadActions.startedAsset)

        val progress = DownloadProgress.Downloading(downloaded = 10L, total = 100L)
        aiAssetDownloadActions.mutableState.value = AiAssetDownloadState.Downloading(
            asset = AiAsset.AsrStreamingZipformer,
            progress = progress,
        )
        advanceUntilIdle()

        assertEquals(AiAssetState.Downloading(progress), viewModel.uiState.value.asrAssetState)

        aiAssetDownloadActions.mutableState.value = AiAssetDownloadState.Completed(
            asset = AiAsset.AsrStreamingZipformer,
            path = "/ai-assets/asr",
        )
        advanceUntilIdle()

        assertEquals(AiAsset.AsrStreamingZipformer, aiAssetRepository.refreshedAsset)
        assertSame(AiAssetState.Ready, viewModel.uiState.value.asrAssetState)
    }

    @Test
    fun asrPartial_updatesInputAndTriggersLiveTranslate() = runTest {
        val translatorRepository = FakeTranslatorRepository(
            isReady = true,
            tokens = listOf("你好"),
        )
        val modelRepository = FakeModelRepository().apply { downloaded = true }
        val viewModel = createViewModel(
            translatorRepository = translatorRepository,
            modelRepository = modelRepository,
        )

        viewModel.initialize()
        advanceUntilIdle()
        viewModel.onEvent(TranslatorEvent.LiveTranslateToggled(true))
        viewModel.onEvent(TranslatorEvent.AsrPartialReceived("hello"))
        advanceTimeBy(600)
        advanceUntilIdle()

        assertEquals("hello", viewModel.uiState.value.inputText)
        assertEquals("hello", translatorRepository.lastText)
        assertEquals("你好", viewModel.uiState.value.outputText)
    }

    @Test
    fun download_setsDownloadingStateAndStartsSelectedModel() = runTest {
        val downloadActions = FakeModelDownloadActions()
        val modelRepository = FakeModelRepository()
        val viewModel = createViewModel(
            modelRepository = modelRepository,
            downloadActions = downloadActions,
        )

        viewModel.onDownload()
        advanceUntilIdle()

        assertSame(ModelStatus.Downloading, viewModel.modelStatus.value)
        assertSame(ModelStatus.Downloading, viewModel.uiState.value.modelStatus)
        assertEquals(modelRepository.currentModel, downloadActions.startedModel)
    }

    @Test
    fun initialize_whenDownloadProgressArrives_updatesStatusAndProgress() = runTest {
        val downloadActions = FakeModelDownloadActions()
        val modelRepository = FakeModelRepository()
        val viewModel = createViewModel(
            modelRepository = modelRepository,
            downloadActions = downloadActions,
        )
        val progress = DownloadProgress.Downloading(downloaded = 10L, total = 100L)

        viewModel.initialize()
        downloadActions.mutableState.value = ModelDownloadState.Downloading(
            model = modelRepository.currentModel,
            progress = progress,
        )
        advanceUntilIdle()

        assertSame(ModelStatus.Downloading, viewModel.modelStatus.value)
        assertSame(ModelStatus.Downloading, viewModel.uiState.value.modelStatus)
        assertEquals(progress, viewModel.downloadProgress.value)
        assertEquals(progress, viewModel.uiState.value.downloadProgress)
    }

    @Test
    fun selectModel_cancelsDownloadAndStoresSelection() = runTest {
        val downloadActions = FakeModelDownloadActions()
        val modelRepository = FakeModelRepository()
        val viewModel = createViewModel(
            modelRepository = modelRepository,
            downloadActions = downloadActions,
        )
        val nextModel = testModel("Q6_K")

        viewModel.onSelectModel(nextModel)
        advanceUntilIdle()

        assertTrue(downloadActions.cancelled)
        assertEquals(nextModel, modelRepository.currentModel)
        assertEquals(nextModel, viewModel.selectedModel.value)
        assertEquals(nextModel, viewModel.uiState.value.selectedModel)
        assertSame(ModelStatus.NotDownloaded, viewModel.modelStatus.value)
    }

    @Test
    fun sourceAndTargetCannotRemainEqualAfterManualSelection() = runTest {
        val viewModel = createViewModel()
        val english = Language("en", "English")

        viewModel.onSourceLangChange(english)

        assertEquals("en", viewModel.sourceLang.value.code)
        assertEquals("en", viewModel.uiState.value.sourceLang.code)
        assertTrue(viewModel.targetLang.value.code != viewModel.sourceLang.value.code)
        assertTrue(viewModel.uiState.value.targetLang.code != viewModel.uiState.value.sourceLang.code)
    }

    private fun createViewModel(
        translatorRepository: FakeTranslatorRepository = FakeTranslatorRepository(),
        modelRepository: FakeModelRepository = FakeModelRepository(),
        aiAssetRepository: FakeAiAssetRepository = FakeAiAssetRepository(),
        voiceInputRepository: FakeVoiceInputRepository = FakeVoiceInputRepository(),
        downloadActions: FakeModelDownloadActions = FakeModelDownloadActions(),
        aiAssetDownloadActions: FakeAiAssetDownloadActions = FakeAiAssetDownloadActions(),
        notifications: FakeModelDownloadNotifications = FakeModelDownloadNotifications(),
    ): TranslatorViewModel =
        TranslatorViewModel(
            translatorRepository = translatorRepository,
            languageRepository = FakeLanguageRepository(),
            modelRepository = modelRepository,
            aiAssetRepository = aiAssetRepository,
            voiceInputRepository = voiceInputRepository,
            modelDownloadController = downloadActions,
            aiAssetDownloadController = aiAssetDownloadActions,
            modelDownloadNotifier = notifications,
            modelLoadFailedMessage = "Model load failed",
            cleanupScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
        )

    private class FakeTranslatorRepository(
        private var isReady: Boolean = true,
        private val tokens: List<String> = emptyList(),
    ) : TranslatorRepository {
        override val state: StateFlow<TranslationEngineState> =
            MutableStateFlow(TranslationEngineState.Ready)
        var translateCalls = 0
            private set
        var lastText: String? = null
            private set

        override fun isModelReady(): Boolean = isReady

        override suspend fun loadModel(path: String) {
            isReady = true
        }

        override fun translate(
            text: String,
            sourceLang: Language,
            targetLang: Language,
            maxTokens: Int,
        ): Flow<String> = flow {
            translateCalls += 1
            lastText = text
            tokens.forEach { emit(it) }
        }

        override suspend fun unloadModel() {
            isReady = false
        }

        override suspend fun destroy() = Unit
    }

    private class FakeModelRepository : ModelRepository {
        var currentModel: ModelOption = testModel("Q4_K_M")
            private set
        var downloaded = false

        override fun allModels(): List<ModelOption> = listOf(currentModel)

        override fun getModelPath(): String = "/models/${currentModel.filename}"

        override fun isModelDownloaded(): Boolean = downloaded

        override fun download(): Flow<DownloadProgress> =
            flow { emit(DownloadProgress.Completed(getModelPath())) }

        override fun getRecommended(): ModelOption = currentModel

        override fun getSelectedModel(): ModelOption = currentModel

        override fun selectModel(model: ModelOption) {
            currentModel = model
        }

        override fun clearAllModels() {
            downloaded = false
        }
    }

    private class FakeLanguageRepository : LanguageRepository {
        private val languages = listOf(
            Language("auto", "Auto"),
            Language("en", "English"),
            Language("fr", "French"),
            Language("zh", "Chinese"),
        )

        override fun allLanguages(): List<Language> = languages

        override fun sourceLanguages(): List<Language> = languages

        override fun targetLanguages(): List<Language> =
            languages.filterNot { isSourceOnly(it.code) }

        override fun isSourceOnly(code: String): Boolean = code == "auto"
    }

    private class FakeAiAssetRepository : AiAssetRepository {
        val asrState = MutableStateFlow<AiAssetState>(AiAssetState.NotDownloaded)
        val ocrState = MutableStateFlow<AiAssetState>(AiAssetState.NotDownloaded)
        var refreshedAsset: AiAsset? = null
            private set
        var downloadedAsset: AiAsset? = null
            private set

        override fun state(asset: AiAsset): StateFlow<AiAssetState> =
            when (asset) {
                AiAsset.AsrStreamingZipformer -> asrState
                AiAsset.OcrPpOcrV5Mobile -> ocrState
            }

        override fun refresh(asset: AiAsset) {
            refreshedAsset = asset
        }

        override fun download(asset: AiAsset): Flow<DownloadProgress> = flow {
            downloadedAsset = asset
            when (asset) {
                AiAsset.AsrStreamingZipformer -> asrState.value = AiAssetState.Ready
                AiAsset.OcrPpOcrV5Mobile -> ocrState.value = AiAssetState.Ready
            }
            emit(DownloadProgress.Completed(localPath(asset)))
        }

        override fun isReady(asset: AiAsset): Boolean =
            state(asset).value is AiAssetState.Ready

        override fun localPath(asset: AiAsset): String =
            "/ai-assets/${asset.name}"
    }

    private class FakeVoiceInputRepository : VoiceInputRepository {
        var startedAssetPath: String? = null
            private set
        var stopped = false
            private set

        override suspend fun start(assetPath: String): VoiceInputState {
            startedAssetPath = assetPath
            return VoiceInputState.Listening
        }

        override fun stop() {
            stopped = true
        }
    }

    private class FakeModelDownloadActions : ModelDownloadActions {
        val mutableState = MutableStateFlow<ModelDownloadState>(
            ModelDownloadState.Idle,
        )
        override val state: StateFlow<ModelDownloadState> = mutableState
        var startedModel: ModelOption? = null
            private set
        var cancelled = false
            private set

        override fun start(model: ModelOption) {
            startedModel = model
        }

        override fun cancel() {
            cancelled = true
        }
    }

    private class FakeAiAssetDownloadActions : AiAssetDownloadActions {
        val mutableState = MutableStateFlow<AiAssetDownloadState>(
            AiAssetDownloadState.Idle,
        )
        override val state: StateFlow<AiAssetDownloadState> = mutableState
        var startedAsset: AiAsset? = null
            private set
        var cancelled = false
            private set

        override fun start(asset: AiAsset) {
            startedAsset = asset
        }

        override fun cancel() {
            cancelled = true
        }
    }

    private class FakeModelDownloadNotifications : ModelDownloadNotifications {
        var completed = false
            private set
        var errorMessage: String? = null
            private set

        override fun showComplete() {
            completed = true
        }

        override fun showError(message: String) {
            errorMessage = message
        }
    }

    private companion object {
        fun testModel(key: String): ModelOption =
            ModelOption(
                key = key,
                name = key,
                description = "$key test model",
                filename = "$key.gguf",
                sizeGb = 1.0f,
                memoryRequirementGb = 2.0f,
            )
    }
}
