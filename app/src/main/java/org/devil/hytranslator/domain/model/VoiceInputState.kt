package org.devil.hytranslator.domain.model

sealed interface VoiceInputState {
    data object Idle : VoiceInputState
    data object NeedsAsrModel : VoiceInputState
    data object Listening : VoiceInputState
    data class Error(val message: String) : VoiceInputState
}
