package org.devil.hytranslator.domain.model

sealed interface VoiceInputEvent {
    data class Partial(val text: String) : VoiceInputEvent
    data class Final(val text: String) : VoiceInputEvent
    data class Level(val value: Float) : VoiceInputEvent
    data class Error(val message: String) : VoiceInputEvent
    data object Stopped : VoiceInputEvent
}
