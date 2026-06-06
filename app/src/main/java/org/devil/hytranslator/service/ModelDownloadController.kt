package org.devil.hytranslator.service

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import org.devil.hytranslator.domain.model.ModelOption

class ModelDownloadController(
    private val context: Context,
) : ModelDownloadActions {
    override val state: StateFlow<ModelDownloadService.State> = ModelDownloadService.state

    override fun start(model: ModelOption) {
        ModelDownloadService.start(context, model)
    }

    override fun cancel() {
        ModelDownloadService.cancel(context)
    }
}
