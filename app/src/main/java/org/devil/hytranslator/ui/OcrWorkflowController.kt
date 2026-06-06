package org.devil.hytranslator.ui

import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class OcrWorkflowController(
    private val scope: CoroutineScope,
    private val recognizeBitmap: suspend (Bitmap) -> String,
    private val recognizeUri: suspend (Uri, String) -> String,
    private val updateOcrFlow: (OcrFlow) -> Unit,
) {
    fun showSourcePicker() {
        updateOcrFlow(OcrFlow.SourcePicker)
    }

    fun showCamera() {
        updateOcrFlow(OcrFlow.CameraActive)
    }

    fun hide() {
        updateOcrFlow(OcrFlow.Hidden)
    }

    fun processBitmap(bitmap: Bitmap, failedMessage: String) {
        processRecognition(failedMessage) {
            recognizeBitmap(bitmap)
        }
    }

    fun processUri(uri: Uri, failedMessage: String) {
        processRecognition(failedMessage) {
            recognizeUri(uri, failedMessage)
        }
    }

    internal fun processRecognition(
        failedMessage: String,
        recognize: suspend () -> String,
    ) {
        updateOcrFlow(OcrFlow.Processing)
        scope.launch {
            updateOcrFlow(
                runCatching { recognize() }
                    .fold(
                        onSuccess = { text -> OcrFlow.Result(text) },
                        onFailure = { error -> OcrFlow.Error(error.message ?: failedMessage) },
                    ),
            )
        }
    }
}
