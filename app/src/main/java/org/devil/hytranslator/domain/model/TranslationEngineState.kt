package org.devil.hytranslator.domain.model

sealed interface TranslationEngineState {
    data object Idle : TranslationEngineState
    data object Loading : TranslationEngineState
    data object Ready : TranslationEngineState
    data object Generating : TranslationEngineState
    data class Error(val message: String) : TranslationEngineState
}
