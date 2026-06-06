package org.devil.hytranslator.di

import android.content.Context
import org.devil.hytranslator.R
import org.devil.hytranslator.data.repository.AiAssetRepositoryImpl
import org.devil.hytranslator.data.repository.LanguageRepositoryImpl
import org.devil.hytranslator.data.repository.ModelRepositoryImpl
import org.devil.hytranslator.data.repository.PaddleLiteOcrTextRepository
import org.devil.hytranslator.data.repository.SherpaOnnxVoiceInputRepository
import org.devil.hytranslator.data.repository.TranslatorRepositoryImpl
import org.devil.hytranslator.domain.repository.AiAssetRepository
import org.devil.hytranslator.domain.repository.LanguageRepository
import org.devil.hytranslator.domain.repository.ModelRepository
import org.devil.hytranslator.domain.repository.TranslatorRepository
import org.devil.hytranslator.domain.repository.VoiceInputRepository
import org.devil.hytranslator.platform.ocr.OcrTextRepository
import org.devil.hytranslator.service.AiAssetDownloadActions
import org.devil.hytranslator.service.AiAssetDownloadController
import org.devil.hytranslator.service.ModelDownloadActions
import org.devil.hytranslator.service.ModelDownloadController
import org.devil.hytranslator.service.ModelDownloadNotifications
import org.devil.hytranslator.service.ModelDownloadNotifier
import org.devil.hytranslator.ui.TranslatorViewModelFactory

interface AppContainer {
    val translatorViewModelFactory: TranslatorViewModelFactory
    fun createOcrTextRepository(): OcrTextRepository
}

class DefaultAppContainer(
    context: Context,
) : AppContainer {
    private val appContext = context.applicationContext

    private val translatorRepository: TranslatorRepository by lazy {
        TranslatorRepositoryImpl(appContext)
    }

    private val languageRepository: LanguageRepository by lazy {
        LanguageRepositoryImpl()
    }

    private val modelRepository: ModelRepository by lazy {
        ModelRepositoryImpl(appContext)
    }

    private val aiAssetRepository: AiAssetRepository by lazy {
        AiAssetRepositoryImpl(appContext)
    }

    override fun createOcrTextRepository(): OcrTextRepository =
        PaddleLiteOcrTextRepository(appContext)

    private val voiceInputRepository: VoiceInputRepository by lazy {
        SherpaOnnxVoiceInputRepository()
    }

    private val modelDownloadController: ModelDownloadActions by lazy {
        ModelDownloadController(appContext)
    }

    private val aiAssetDownloadController: AiAssetDownloadActions by lazy {
        AiAssetDownloadController(appContext)
    }

    private val modelDownloadNotifier: ModelDownloadNotifications by lazy {
        ModelDownloadNotifier(appContext)
    }

    override val translatorViewModelFactory: TranslatorViewModelFactory by lazy {
        TranslatorViewModelFactory(
            translatorRepository = translatorRepository,
            languageRepository = languageRepository,
            modelRepository = modelRepository,
            aiAssetRepository = aiAssetRepository,
            voiceInputRepository = voiceInputRepository,
            modelDownloadController = modelDownloadController,
            aiAssetDownloadController = aiAssetDownloadController,
            modelDownloadNotifier = modelDownloadNotifier,
            modelLoadFailedMessage = appContext.getString(R.string.model_load_failed),
        )
    }
}
