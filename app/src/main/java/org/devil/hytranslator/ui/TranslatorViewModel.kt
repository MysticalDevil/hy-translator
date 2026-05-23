package org.devil.hytranslator.ui

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.devil.hytranslator.data.ModelOptions
import org.devil.hytranslator.data.repository.ModelRepositoryImpl
import org.devil.hytranslator.data.repository.TranslatorRepositoryImpl
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.domain.model.Language
import org.devil.hytranslator.domain.model.ModelOption
import org.devil.hytranslator.domain.model.ModelStatus
import org.devil.hytranslator.domain.repository.LanguageRepository
import org.devil.hytranslator.domain.repository.ModelRepository
import org.devil.hytranslator.domain.repository.TranslatorRepository

class TranslatorViewModel(application: Application) : AndroidViewModel(application) {

    private val translatorRepository: TranslatorRepository = TranslatorRepositoryImpl(application)
    private val modelRepository: ModelRepositoryImpl = ModelRepositoryImpl(application)
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

    private val prefs = application.getSharedPreferences("model_prefs", 0)
    private var _selectedModel: ModelOption

    init {
        val savedKey = prefs.getString("model_key", null)
        _selectedModel = if (savedKey != null) {
            ModelOptions.getByKey(savedKey)
        } else {
            modelRepository.getRecommended()
        }
        prefs.edit { putString("model_key", _selectedModel.key) }
        modelRepository.setModelFilename(_selectedModel.filename)

        if (modelRepository.isModelDownloaded()) {
            loadModel()
        }
    }

    private val _selectedModelFlow = MutableStateFlow(_selectedModel)
    val selectedModel: StateFlow<ModelOption> = _selectedModelFlow.asStateFlow()

    private var generationJob: Job? = null
    private var downloadJob: Job? = null

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
        generationJob = viewModelScope.launch {
            translatorRepository.translate(
                text = _inputText.value,
                sourceLang = _sourceLang.value,
                targetLang = _targetLang.value,
            ).collect { token ->
                _outputText.update { it + token }
            }
            _isTranslating.value = false
            generationJob = null
        }
    }

    fun onCancel() {
        generationJob?.cancel()
        _isTranslating.value = false
        generationJob = null
    }

    fun onSelectModel(model: ModelOption) {
        if (model.key == _selectedModel.key) return

        generationJob?.cancel()
        generationJob = null
        downloadJob?.cancel()
        downloadJob = null
        _isTranslating.value = false

        _selectedModel = model
        _selectedModelFlow.value = model
        prefs.edit { putString("model_key", model.key) }
        modelRepository.setModelFilename(model.filename)

        if (modelRepository.isModelDownloaded()) {
            loadModel()
        } else {
            _modelStatus.value = ModelStatus.NotDownloaded
            _downloadProgress.value = null
        }
    }

    fun onDownload() {
        if (downloadJob?.isActive == true) return

        _modelStatus.value = ModelStatus.Downloading
        _downloadProgress.value = null
        downloadJob = viewModelScope.launch {
            try {
                modelRepository.download().collect { progress ->
                    _downloadProgress.value = progress
                    when (progress) {
                        is DownloadProgress.Completed -> loadModel()
                        is DownloadProgress.Error -> {
                            _modelStatus.value = ModelStatus.Error(progress.message)
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                _modelStatus.value = ModelStatus.Error(
                    e.message ?: getApplication<Application>()
                        .getString(org.devil.hytranslator.R.string.model_load_failed),
                )
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
        translatorRepository.destroy()
    }

    private fun loadModel() {
        _modelStatus.value = ModelStatus.Loading
        viewModelScope.launch {
            try {
                if (translatorRepository.isModelReady()) {
                    translatorRepository.cancel()
                }
                translatorRepository.loadModel(modelRepository.getModelPath())
                _modelStatus.value = ModelStatus.Ready
            } catch (e: Exception) {
                _modelStatus.value = ModelStatus.Error(
                    e.message ?: getApplication<Application>()
                        .getString(org.devil.hytranslator.R.string.model_load_failed),
                )
            }
        }
    }
}
