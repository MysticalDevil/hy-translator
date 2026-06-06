package org.devil.hytranslator.service

import kotlinx.coroutines.flow.StateFlow
import org.devil.hytranslator.domain.model.ModelOption

interface ModelDownloadActions {
    val state: StateFlow<ModelDownloadService.State>

    fun start(model: ModelOption)

    fun cancel()
}
