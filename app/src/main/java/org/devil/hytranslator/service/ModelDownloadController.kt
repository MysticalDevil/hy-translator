package org.devil.hytranslator.service

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.devil.hytranslator.domain.model.ModelDownloadState
import org.devil.hytranslator.domain.model.ModelOption
import org.devil.hytranslator.platform.download.ModelDownloadStateStore

class ModelDownloadController(
    private val context: Context,
) : ModelDownloadActions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateStore = ModelDownloadStateStore(context)

    override val state: StateFlow<ModelDownloadState> = stateStore.state.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = ModelDownloadState.Idle,
    )

    override fun start(model: ModelOption) {
        ModelDownloadService.start(context, model)
    }

    override fun cancel() {
        ModelDownloadService.cancel(context)
    }
}
