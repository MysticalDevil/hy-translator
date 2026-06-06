package org.devil.hytranslator.service

import kotlinx.coroutines.flow.StateFlow
import org.devil.hytranslator.domain.model.ModelDownloadState
import org.devil.hytranslator.domain.model.ModelOption

interface ModelDownloadActions {
    val state: StateFlow<ModelDownloadState>

    fun start(model: ModelOption)

    fun cancel()
}
