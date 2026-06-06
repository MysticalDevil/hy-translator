package org.devil.hytranslator.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.AiAssetDownloadState
import org.devil.hytranslator.domain.model.AiAssetState
import org.devil.hytranslator.domain.model.Language
import org.devil.hytranslator.domain.model.ModelDownloadState
import org.devil.hytranslator.domain.model.ModelOption
import org.devil.hytranslator.domain.model.ModelStatus
import org.devil.hytranslator.domain.model.VoiceInputState
import org.devil.hytranslator.domain.repository.AiAssetRepository
import org.devil.hytranslator.domain.repository.LanguageRepository
import org.devil.hytranslator.domain.repository.ModelRepository
import org.devil.hytranslator.domain.repository.TranslatorRepository
import org.devil.hytranslator.service.AiAssetDownloadActions
import org.devil.hytranslator.service.ModelDownloadActions
import org.devil.hytranslator.service.ModelDownloadNotifications

class TranslatorViewModel(
    private val translatorRepository: TranslatorRepository,
    private val languageRepository: LanguageRepository,
    private val modelRepository: ModelRepository,
    private val aiAssetRepository: AiAssetRepository,
    private val modelDownloadController: ModelDownloadActions,
    private val aiAssetDownloadController: AiAssetDownloadActions,
    private val modelDownloadNotifier: ModelDownloadNotifications,
    private val modelLoadFailedMessage: String,
    private val cleanupScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ViewModel() {
    private val compatibilitySharing = SharingStarted.Eagerly
    private val initialSourceLang = languageRepository.sourceLanguages().first()
    private val initialTargetLang = languageRepository.targetLanguages()
        .first { it.code != "auto" }
    private val initialSelectedModel = modelRepository.getSelectedModel()

    private val _uiState = MutableStateFlow(
        TranslatorUiState(
            inputText = "",
            outputText = "",
            sourceLang = initialSourceLang,
            targetLang = initialTargetLang,
            isTranslating = false,
            modelStatus = ModelStatus.NotDownloaded,
            downloadProgress = null,
            selectedModel = initialSelectedModel,
        ),
    )
    val uiState: StateFlow<TranslatorUiState> = _uiState.asStateFlow()

    val inputText: StateFlow<String> = uiState.map { it.inputText }
        .stateIn(viewModelScope, compatibilitySharing, "")

    val outputText: StateFlow<String> = uiState.map { it.outputText }
        .stateIn(viewModelScope, compatibilitySharing, "")

    val sourceLang: StateFlow<Language> = uiState.map { it.sourceLang }
        .stateIn(viewModelScope, compatibilitySharing, initialSourceLang)

    val targetLang: StateFlow<Language> = uiState.map { it.targetLang }
        .stateIn(viewModelScope, compatibilitySharing, initialTargetLang)

    val isTranslating: StateFlow<Boolean> = uiState.map { it.isTranslating }
        .stateIn(viewModelScope, compatibilitySharing, false)

    val modelStatus: StateFlow<ModelStatus> = uiState.map { it.modelStatus }
        .stateIn(
            viewModelScope,
            compatibilitySharing,
            ModelStatus.NotDownloaded,
        )

    val downloadProgress = uiState.map { it.downloadProgress }
        .stateIn(viewModelScope, compatibilitySharing, null)

    val selectedModel: StateFlow<ModelOption> = uiState.map { it.selectedModel }
        .stateIn(viewModelScope, compatibilitySharing, initialSelectedModel)

    private var generationJob: Job? = null
    private var liveTranslateJob: Job? = null
    private var downloadJob: Job? = null
    private var loadJob: Job? = null
    private var aiAssetJob: Job? = null
    private var aiAssetServiceJob: Job? = null
    private var modelOperationId = 0L
    private var translationOperationId = 0L
    private var initialized = false
    private var handledCompletedDownloadPath: String? = null

    fun initialize() {
        if (initialized) return
        initialized = true
        aiAssetRepository.refresh(AiAsset.AsrStreamingZipformer)
        aiAssetRepository.refresh(AiAsset.OcrPpOcrV5Mobile)
        observeDownloadService()
        observeAiAssetDownloadService()
        observeAiAssets()
        if (modelRepository.isModelDownloaded()) {
            loadModel()
        }
    }

    fun onEvent(event: TranslatorEvent) {
        when (event) {
            is TranslatorEvent.InputChanged -> onInputTextChange(event.text)
            is TranslatorEvent.SourceLanguageChanged -> onSourceLangChange(event.language)
            is TranslatorEvent.TargetLanguageChanged -> onTargetLangChange(event.language)
            is TranslatorEvent.ModelSelected -> onSelectModel(event.model)
            is TranslatorEvent.LiveTranslateToggled -> onLiveTranslateToggled(event.enabled)
            is TranslatorEvent.VoiceInputToggled -> onVoiceInputToggled(event.enabled)
            is TranslatorEvent.AsrPartialReceived -> onAsrTextReceived(event.text)
            is TranslatorEvent.AsrFinalReceived -> onAsrTextReceived(event.text)
            is TranslatorEvent.RefreshAiAsset -> aiAssetRepository.refresh(event.asset)
            is TranslatorEvent.DownloadAiAsset -> onDownloadAiAsset(event.asset)
            TranslatorEvent.Translate -> onTranslate()
            TranslatorEvent.CancelTranslation -> onCancel()
            TranslatorEvent.DownloadModel -> onDownload()
            TranslatorEvent.ClearAllModels -> onClearAllModels()
            TranslatorEvent.SwapLanguages -> onSwapLanguages()
        }
    }

    fun onInputTextChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
        scheduleLiveTranslateIfEnabled()
    }

    fun onSourceLangChange(lang: Language) {
        _uiState.update { state ->
            val targetLang = if (lang.code == state.targetLang.code) {
                languageRepository.targetLanguages().first { it.code != lang.code }
            } else {
                state.targetLang
            }
            state.copy(sourceLang = lang, targetLang = targetLang)
        }
        scheduleLiveTranslateIfEnabled()
    }

    fun onTargetLangChange(lang: Language) {
        _uiState.update { state ->
            val sourceLang = if (
                lang.code == state.sourceLang.code &&
                !languageRepository.isSourceOnly(state.sourceLang.code)
            ) {
                languageRepository.sourceLanguages().first { it.code != lang.code }
            } else {
                state.sourceLang
            }
            state.copy(sourceLang = sourceLang, targetLang = lang)
        }
        scheduleLiveTranslateIfEnabled()
    }

    fun onTranslate() {
        liveTranslateJob?.cancel()
        startTranslation(cancelRunning = true)
    }

    fun onCancel() {
        liveTranslateJob?.cancel()
        generationJob?.cancel()
        _uiState.update { it.copy(isTranslating = false) }
        generationJob = null
    }

    fun onLiveTranslateToggled(enabled: Boolean) {
        _uiState.update { it.copy(isLiveTranslateEnabled = enabled) }
        if (enabled) {
            scheduleLiveTranslateIfEnabled()
        } else {
            liveTranslateJob?.cancel()
            liveTranslateJob = null
        }
    }

    fun onVoiceInputToggled(enabled: Boolean) {
        if (!enabled) {
            _uiState.update { it.copy(voiceInputState = VoiceInputState.Idle) }
            return
        }

        val state = _uiState.value.asrAssetState
        _uiState.update {
            it.copy(
                voiceInputState = if (state is AiAssetState.Ready) {
                    VoiceInputState.Listening
                } else {
                    VoiceInputState.NeedsAsrModel
                },
            )
        }
    }

    fun onAsrTextReceived(text: String) {
        _uiState.update { it.copy(inputText = text) }
        scheduleLiveTranslateIfEnabled()
    }

    fun onDownloadAiAsset(asset: AiAsset) {
        observeAiAssetDownloadService()
        aiAssetDownloadController.start(asset)
    }

    fun onSelectModel(model: ModelOption) {
        if (model.key == _uiState.value.selectedModel.key) return

        val oldGenerationJob = generationJob
        liveTranslateJob?.cancel()
        oldGenerationJob?.cancel()
        modelDownloadController.cancel()
        loadJob?.cancel()
        loadJob = null
        _uiState.update {
            it.copy(
                isTranslating = false,
                selectedModel = model,
            )
        }

        modelRepository.selectModel(model)
        val operationId = ++modelOperationId

        if (modelRepository.isModelDownloaded()) {
            loadModel(model, operationId, oldGenerationJob)
        } else {
            unloadCurrentModel(model, operationId, oldGenerationJob)
        }
    }

    fun onDownload() {
        _uiState.update {
            it.copy(
                modelStatus = ModelStatus.Downloading,
                downloadProgress = null,
            )
        }
        observeDownloadService()
        modelDownloadController.start(_uiState.value.selectedModel)
    }

    fun onClearAllModels() {
        val oldGenerationJob = generationJob
        modelDownloadController.cancel()
        oldGenerationJob?.cancel()
        loadJob?.cancel()
        loadJob = null
        ++modelOperationId
        _uiState.update {
            it.copy(
                isTranslating = false,
                modelStatus = ModelStatus.NotDownloaded,
                downloadProgress = null,
            )
        }
        loadJob = viewModelScope.launch {
            try {
                oldGenerationJob?.join()
                if (translatorRepository.isModelReady()) {
                    translatorRepository.unloadModel()
                }
                modelRepository.clearAllModels()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setModelStatus(ModelStatus.Error(e.message ?: modelLoadFailedMessage))
            } finally {
                loadJob = null
            }
        }
    }

    private fun observeDownloadService() {
        if (downloadJob?.isActive == true) return

        downloadJob = viewModelScope.launch {
            modelDownloadController.state.collect { state ->
                when (state) {
                    is ModelDownloadState.Idle -> {}
                    is ModelDownloadState.Downloading -> {
                        if (state.model.key == _uiState.value.selectedModel.key) {
                            _uiState.update {
                                it.copy(
                                    modelStatus = ModelStatus.Downloading,
                                    downloadProgress = state.progress,
                                )
                            }
                        }
                    }
                    is ModelDownloadState.Completed -> {
                        if (
                            state.model.key == _uiState.value.selectedModel.key &&
                            handledCompletedDownloadPath != state.path
                        ) {
                            handledCompletedDownloadPath = state.path
                            val operationId = ++modelOperationId
                            loadModel(
                                model = state.model,
                                operationId = operationId,
                                showDownloadCompleteNotification = true,
                            )
                        }
                    }
                    is ModelDownloadState.Error -> {
                        if (state.model.key == _uiState.value.selectedModel.key) {
                            setModelStatus(ModelStatus.Error(state.message))
                        }
                    }
                }
            }
        }
    }

    private fun observeAiAssets() {
        if (aiAssetJob?.isActive == true) return

        aiAssetJob = viewModelScope.launch {
            launch {
                aiAssetRepository.state(AiAsset.AsrStreamingZipformer).collect { assetState ->
                    _uiState.update {
                        it.copy(
                            asrAssetState = assetState,
                            voiceInputState = if (
                                it.voiceInputState is VoiceInputState.NeedsAsrModel &&
                                assetState is AiAssetState.Ready
                            ) {
                                VoiceInputState.Idle
                            } else {
                                it.voiceInputState
                            },
                        )
                    }
                }
            }
            launch {
                aiAssetRepository.state(AiAsset.OcrPpOcrV5Mobile).collect { assetState ->
                    _uiState.update { it.copy(ocrAssetState = assetState) }
                }
            }
        }
    }

    private fun observeAiAssetDownloadService() {
        if (aiAssetServiceJob?.isActive == true) return

        aiAssetServiceJob = viewModelScope.launch {
            aiAssetDownloadController.state.collect { state ->
                when (state) {
                    is AiAssetDownloadState.Idle -> {}
                    is AiAssetDownloadState.Downloading -> {
                        setAiAssetState(
                            asset = state.asset,
                            assetState = AiAssetState.Downloading(state.progress),
                        )
                    }
                    is AiAssetDownloadState.Completed -> {
                        aiAssetRepository.refresh(state.asset)
                        setAiAssetState(state.asset, AiAssetState.Ready)
                    }
                    is AiAssetDownloadState.Error -> {
                        setAiAssetState(
                            asset = state.asset,
                            assetState = AiAssetState.Error(state.message),
                        )
                    }
                }
            }
        }
    }

    fun onSwapLanguages() {
        _uiState.update {
            it.copy(
                sourceLang = it.targetLang,
                targetLang = it.sourceLang,
            )
        }
    }

    fun isSwapEnabled(): Boolean =
        !languageRepository.isSourceOnly(_uiState.value.sourceLang.code)

    val allLanguages: List<Language> get() = languageRepository.allLanguages()
    val sourceLanguages: List<Language> get() = languageRepository.sourceLanguages()
    val targetLanguages: List<Language> get() = languageRepository.targetLanguages()
    val allModels: List<ModelOption> get() = modelRepository.allModels()
    val recommendedModel: ModelOption get() = modelRepository.getRecommended()

    override fun onCleared() {
        super.onCleared()
        cleanupScope.launch {
            translatorRepository.destroy()
        }
    }

    private fun loadModel(
        model: ModelOption = _uiState.value.selectedModel,
        operationId: Long = ++modelOperationId,
        previousGenerationJob: Job? = null,
        showDownloadCompleteNotification: Boolean = false,
    ) {
        loadJob?.cancel()
        setModelStatus(ModelStatus.Loading)
        val modelPath = modelRepository.getModelPath()
        loadJob = viewModelScope.launch {
            try {
                previousGenerationJob?.join()
                if (translatorRepository.isModelReady()) {
                    translatorRepository.unloadModel()
                }
                translatorRepository.loadModel(modelPath)
                if (operationId == modelOperationId &&
                    model.key == _uiState.value.selectedModel.key
                ) {
                    _uiState.update {
                        it.copy(
                            modelStatus = ModelStatus.Ready,
                            downloadProgress = null,
                        )
                    }
                    scheduleLiveTranslateIfEnabled()
                    if (showDownloadCompleteNotification) {
                        modelDownloadNotifier.showComplete()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (operationId == modelOperationId &&
                    model.key == _uiState.value.selectedModel.key
                ) {
                    modelDownloadNotifier.showError(e.message ?: modelLoadFailedMessage)
                    setModelStatus(ModelStatus.Error(e.message ?: modelLoadFailedMessage))
                }
            } finally {
                if (operationId == modelOperationId) {
                    loadJob = null
                }
            }
        }
    }

    private fun unloadCurrentModel(
        model: ModelOption,
        operationId: Long,
        previousGenerationJob: Job?,
    ) {
        _uiState.update {
            it.copy(
                modelStatus = ModelStatus.NotDownloaded,
                downloadProgress = null,
            )
        }
        loadJob = viewModelScope.launch {
            try {
                previousGenerationJob?.join()
                if (translatorRepository.isModelReady()) {
                    translatorRepository.unloadModel()
                }
                if (operationId == modelOperationId &&
                    model.key == _uiState.value.selectedModel.key
                ) {
                    setModelStatus(ModelStatus.NotDownloaded)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (operationId == modelOperationId &&
                    model.key == _uiState.value.selectedModel.key
                ) {
                    setModelStatus(ModelStatus.Error(e.message ?: modelLoadFailedMessage))
                }
            } finally {
                if (operationId == modelOperationId) {
                    loadJob = null
                }
            }
        }
    }

    private fun setModelStatus(status: ModelStatus) {
        _uiState.update { it.copy(modelStatus = status) }
    }

    private fun setAiAssetState(asset: AiAsset, assetState: AiAssetState) {
        _uiState.update {
            when (asset) {
                AiAsset.AsrStreamingZipformer -> it.copy(
                    asrAssetState = assetState,
                    voiceInputState = if (
                        it.voiceInputState is VoiceInputState.NeedsAsrModel &&
                        assetState is AiAssetState.Ready
                    ) {
                        VoiceInputState.Idle
                    } else {
                        it.voiceInputState
                    },
                )
                AiAsset.OcrPpOcrV5Mobile -> it.copy(ocrAssetState = assetState)
            }
        }
    }

    private fun scheduleLiveTranslateIfEnabled() {
        liveTranslateJob?.cancel()
        val state = _uiState.value
        if (!state.isLiveTranslateEnabled) return
        if (state.inputText.isBlank()) return
        if (state.modelStatus !is ModelStatus.Ready) return
        if (!translatorRepository.isModelReady()) return

        liveTranslateJob = viewModelScope.launch {
            delay(LIVE_TRANSLATE_DEBOUNCE_MS)
            startTranslation(cancelRunning = true)
        }
    }

    private fun startTranslation(cancelRunning: Boolean) {
        val state = _uiState.value
        if (state.inputText.isBlank() || !translatorRepository.isModelReady()) return

        val activeGenerationJob = generationJob
        if (activeGenerationJob?.isActive == true) {
            if (!cancelRunning) return
            activeGenerationJob.cancel()
        }

        val operationId = ++translationOperationId
        _uiState.update { it.copy(isTranslating = true, outputText = "") }
        generationJob = viewModelScope.launch {
            try {
                translatorRepository.translate(
                    text = state.inputText,
                    sourceLang = state.sourceLang,
                    targetLang = state.targetLang,
                ).collect { token ->
                    if (operationId == translationOperationId) {
                        _uiState.update { it.copy(outputText = it.outputText + token) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (operationId == translationOperationId) {
                    setModelStatus(ModelStatus.Error(e.message ?: modelLoadFailedMessage))
                }
            } finally {
                if (operationId == translationOperationId) {
                    _uiState.update { it.copy(isTranslating = false) }
                    generationJob = null
                }
            }
        }
    }

    private companion object {
        const val LIVE_TRANSLATE_DEBOUNCE_MS = 600L
    }
}
