package org.devil.hytranslator.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.devil.hytranslator.domain.repository.AiAssetRepository
import org.devil.hytranslator.domain.repository.LanguageRepository
import org.devil.hytranslator.domain.repository.ModelRepository
import org.devil.hytranslator.domain.repository.TranslatorRepository
import org.devil.hytranslator.domain.repository.VoiceInputRepository
import org.devil.hytranslator.service.AiAssetDownloadActions
import org.devil.hytranslator.service.ModelDownloadActions
import org.devil.hytranslator.service.ModelDownloadNotifications

class TranslatorViewModelFactory(
    private val translatorRepository: TranslatorRepository,
    private val languageRepository: LanguageRepository,
    private val modelRepository: ModelRepository,
    private val aiAssetRepository: AiAssetRepository,
    private val voiceInputRepository: VoiceInputRepository,
    private val modelDownloadController: ModelDownloadActions,
    private val aiAssetDownloadController: AiAssetDownloadActions,
    private val modelDownloadNotifier: ModelDownloadNotifications,
    private val modelLoadFailedMessage: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(TranslatorViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }

        return TranslatorViewModel(
            translatorRepository = translatorRepository,
            languageRepository = languageRepository,
            modelRepository = modelRepository,
            aiAssetRepository = aiAssetRepository,
            voiceInputRepository = voiceInputRepository,
            modelDownloadController = modelDownloadController,
            aiAssetDownloadController = aiAssetDownloadController,
            modelDownloadNotifier = modelDownloadNotifier,
            modelLoadFailedMessage = modelLoadFailedMessage,
        ) as T
    }
}
