package org.devil.hytranslator.ui

import org.devil.hytranslator.domain.model.Language
import org.devil.hytranslator.domain.model.ModelOption
import org.devil.hytranslator.domain.model.AiAsset

sealed interface TranslatorEvent {
    data class InputChanged(val text: String) : TranslatorEvent
    data class SourceLanguageChanged(val language: Language) : TranslatorEvent
    data class TargetLanguageChanged(val language: Language) : TranslatorEvent
    data class ModelSelected(val model: ModelOption) : TranslatorEvent
    data class LiveTranslateToggled(val enabled: Boolean) : TranslatorEvent
    data class VoiceInputToggled(val enabled: Boolean) : TranslatorEvent
    data class VoiceInputPermissionDenied(val message: String) : TranslatorEvent
    data class AsrPartialReceived(val text: String) : TranslatorEvent
    data class AsrFinalReceived(val text: String) : TranslatorEvent
    data class RefreshAiAsset(val asset: AiAsset) : TranslatorEvent
    data class DownloadAiAsset(val asset: AiAsset) : TranslatorEvent
    data object Translate : TranslatorEvent
    data object CancelTranslation : TranslatorEvent
    data object DownloadModel : TranslatorEvent
    data object ClearAllModels : TranslatorEvent
    data object SwapLanguages : TranslatorEvent
}
