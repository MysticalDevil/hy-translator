package org.devil.hytranslator.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.domain.model.Language
import org.devil.hytranslator.domain.model.ModelOption
import org.devil.hytranslator.domain.model.ModelStatus
import org.devil.hytranslator.domain.repository.LanguageRepository
import org.devil.hytranslator.domain.repository.ModelRepository
import org.devil.hytranslator.domain.repository.TranslatorRepository
import org.devil.hytranslator.service.ModelDownloadController
import org.devil.hytranslator.service.ModelDownloadNotifier
import org.devil.hytranslator.service.ModelDownloadService

class TranslatorViewModel(
    private val translatorRepository: TranslatorRepository,
    private val modelRepository: ModelRepository,
    private val modelDownloadController: ModelDownloadController,
    private val modelDownloadNotifier: ModelDownloadNotifier,
    private val modelLoadFailedMessage: String,
    private val cleanupScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ViewModel() {

    private val languageRepository: LanguageRepository = object : LanguageRepository {
        override fun allLanguages(): List<Language> =
            org.devil.hytranslator.data.Languages.all
        override fun sourceLanguages(): List<Language> =
            org.devil.hytranslator.data.Languages.sourceLanguages()
        override fun targetLanguages(): List<Language> =
            org.devil.hytranslator.data.Languages.targetLanguages()
        override fun isSourceOnly(code: String): Boolean =
            org.devil.hytranslator.data.Languages.isSourceOnly(code)
    }

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _outputText = MutableStateFlow("")
    val outputText: StateFlow<String> = _outputText.asStateFlow()

    private val _sourceLang = MutableStateFlow(languageRepository.sourceLanguages().first())
    val sourceLang: StateFlow<Language> = _sourceLang.asStateFlow()

    private val _targetLang = MutableStateFlow(
        languageRepository.targetLanguages().first { it.code != "auto" },
    )
    val targetLang: StateFlow<Language> = _targetLang.asStateFlow()

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()

    private val _modelStatus = MutableStateFlow<ModelStatus>(ModelStatus.NotDownloaded)
    val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    private var _selectedModel: ModelOption = modelRepository.getSelectedModel()

    private val _selectedModelFlow = MutableStateFlow(_selectedModel)
    val selectedModel: StateFlow<ModelOption> = _selectedModelFlow.asStateFlow()

    private var generationJob: Job? = null
    private var downloadJob: Job? = null
    private var loadJob: Job? = null
    private var modelOperationId = 0L
    private var initialized = false
    private var handledCompletedDownloadPath: String? = null

    fun initialize() {
        if (initialized) return
        initialized = true
        observeDownloadService()
        if (modelRepository.isModelDownloaded()) {
            loadModel()
        }
    }

    fun onInputTextChange(text: String) {
        _inputText.value = text
    }

    fun onSourceLangChange(lang: Language) {
        _sourceLang.value = lang
        if (lang.code == _targetLang.value.code) {
            _targetLang.value = languageRepository.targetLanguages()
                .first { it.code != lang.code }
        }
    }

    fun onTargetLangChange(lang: Language) {
        _targetLang.value = lang
        if (lang.code == _sourceLang.value.code &&
            !languageRepository.isSourceOnly(_sourceLang.value.code)
        ) {
            _sourceLang.value = languageRepository.sourceLanguages()
                .first { it.code != lang.code }
        }
    }

    fun onTranslate() {
        if (_inputText.value.isBlank() || !translatorRepository.isModelReady()) return
        if (_isTranslating.value) return

        _isTranslating.value = true
        _outputText.value = ""
        val text = _inputText.value
        val sourceLang = _sourceLang.value
        val targetLang = _targetLang.value
        generationJob = viewModelScope.launch {
            try {
                translatorRepository.translate(
                    text = text,
                    sourceLang = sourceLang,
                    targetLang = targetLang,
                ).collect { token ->
                    _outputText.update { it + token }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _modelStatus.value = ModelStatus.Error(
                    e.message ?: modelLoadFailedMessage,
                )
            } finally {
                _isTranslating.value = false
                generationJob = null
            }
        }
    }

    fun onCancel() {
        generationJob?.cancel()
        _isTranslating.value = false
        generationJob = null
    }

    fun onSelectModel(model: ModelOption) {
        if (model.key == _selectedModel.key) return

        val oldGenerationJob = generationJob
        oldGenerationJob?.cancel()
        modelDownloadController.cancel()
        loadJob?.cancel()
        loadJob = null
        _isTranslating.value = false

        _selectedModel = model
        _selectedModelFlow.value = model
        modelRepository.selectModel(model)
        val operationId = ++modelOperationId

        if (modelRepository.isModelDownloaded()) {
            loadModel(model, operationId, oldGenerationJob)
        } else {
            unloadCurrentModel(model, operationId, oldGenerationJob)
        }
    }

    fun onDownload() {
        _modelStatus.value = ModelStatus.Downloading
        _downloadProgress.value = null
        observeDownloadService()
        modelDownloadController.start(_selectedModel)
    }

    fun onClearAllModels() {
        val oldGenerationJob = generationJob
        modelDownloadController.cancel()
        oldGenerationJob?.cancel()
        loadJob?.cancel()
        loadJob = null
        ++modelOperationId
        _isTranslating.value = false
        _modelStatus.value = ModelStatus.NotDownloaded
        _downloadProgress.value = null
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
                _modelStatus.value = ModelStatus.Error(
                    e.message ?: modelLoadFailedMessage,
                )
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
                    is ModelDownloadService.State.Idle -> {}
                    is ModelDownloadService.State.Downloading -> {
                        if (state.model.key == _selectedModel.key) {
                            _modelStatus.value = ModelStatus.Downloading
                            _downloadProgress.value = state.progress
                        }
                    }
                    is ModelDownloadService.State.Completed -> {
                        if (
                            state.model.key == _selectedModel.key &&
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
                    is ModelDownloadService.State.Error -> {
                        if (state.model.key == _selectedModel.key) {
                            _modelStatus.value = ModelStatus.Error(state.message)
                        }
                    }
                }
            }
        }
    }

    fun onSwapLanguages() {
        val src = _sourceLang.value
        val tgt = _targetLang.value
        _sourceLang.value = tgt
        _targetLang.value = src
    }

    fun isSwapEnabled(): Boolean = !languageRepository.isSourceOnly(_sourceLang.value.code)

    val allLanguages: List<Language> get() = languageRepository.allLanguages()

    override fun onCleared() {
        super.onCleared()
        cleanupScope.launch {
            translatorRepository.destroy()
        }
    }

    private fun loadModel(
        model: ModelOption = _selectedModel,
        operationId: Long = ++modelOperationId,
        previousGenerationJob: Job? = null,
        showDownloadCompleteNotification: Boolean = false,
    ) {
        loadJob?.cancel()
        _modelStatus.value = ModelStatus.Loading
        val modelPath = modelRepository.getModelPath()
        loadJob = viewModelScope.launch {
            try {
                previousGenerationJob?.join()
                if (translatorRepository.isModelReady()) {
                    translatorRepository.unloadModel()
                }
                translatorRepository.loadModel(modelPath)
                if (operationId == modelOperationId && model.key == _selectedModel.key) {
                    _modelStatus.value = ModelStatus.Ready
                    _downloadProgress.value = null
                    if (showDownloadCompleteNotification) {
                        modelDownloadNotifier.showComplete()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (operationId == modelOperationId && model.key == _selectedModel.key) {
                    modelDownloadNotifier.showError(e.message ?: modelLoadFailedMessage)
                    _modelStatus.value = ModelStatus.Error(
                        e.message ?: modelLoadFailedMessage,
                    )
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
        _modelStatus.value = ModelStatus.NotDownloaded
        _downloadProgress.value = null
        loadJob = viewModelScope.launch {
            try {
                previousGenerationJob?.join()
                if (translatorRepository.isModelReady()) {
                    translatorRepository.unloadModel()
                }
                if (operationId == modelOperationId && model.key == _selectedModel.key) {
                    _modelStatus.value = ModelStatus.NotDownloaded
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (operationId == modelOperationId && model.key == _selectedModel.key) {
                    _modelStatus.value = ModelStatus.Error(
                        e.message ?: modelLoadFailedMessage,
                    )
                }
            } finally {
                if (operationId == modelOperationId) {
                    loadJob = null
                }
            }
        }
    }
}
