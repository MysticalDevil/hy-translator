package org.devil.hytranslator.service

import android.os.Build

internal enum class DownloadTransport {
    ForegroundService,
    UserInitiatedDataTransferJob,
}

internal object DownloadStartPolicy {
    fun choose(apiLevel: Int = Build.VERSION.SDK_INT): DownloadTransport =
        if (apiLevel >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            DownloadTransport.UserInitiatedDataTransferJob
        } else {
            DownloadTransport.ForegroundService
        }
}
