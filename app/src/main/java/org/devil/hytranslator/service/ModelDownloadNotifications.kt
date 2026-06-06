package org.devil.hytranslator.service

interface ModelDownloadNotifications {
    fun showComplete()

    fun showError(message: String)
}
