package org.devil.hytranslator.ui

sealed class ModelStatus {
    data object NotDownloaded : ModelStatus()
    data object Downloading : ModelStatus()
    data object Loading : ModelStatus()
    data object Ready : ModelStatus()
    data class Error(val message: String) : ModelStatus()
}
