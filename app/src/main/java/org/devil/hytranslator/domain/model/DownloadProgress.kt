package org.devil.hytranslator.domain.model

sealed class DownloadProgress {
    data class Started(val total: Long, val existing: Long) : DownloadProgress()
    data class Downloading(val downloaded: Long, val total: Long) : DownloadProgress()
    data class Completed(val path: String) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}
