package org.devil.hytranslator.ui

import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.domain.model.Language
import org.devil.hytranslator.domain.model.ModelOption
import org.devil.hytranslator.domain.model.ModelStatus
import org.devil.hytranslator.domain.model.AiAssetState
import org.devil.hytranslator.domain.model.VoiceInputState

data class TranslatorUiState(
    val inputText: String,
    val outputText: String,
    val sourceLang: Language,
    val targetLang: Language,
    val isTranslating: Boolean,
    val modelStatus: ModelStatus,
    val downloadProgress: DownloadProgress?,
    val selectedModel: ModelOption,
    val isLiveTranslateEnabled: Boolean = false,
    val asrAssetState: AiAssetState = AiAssetState.NotDownloaded,
    val ocrAssetState: AiAssetState = AiAssetState.NotDownloaded,
    val voiceInputState: VoiceInputState = VoiceInputState.Idle,
)
