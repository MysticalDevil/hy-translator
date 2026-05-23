package org.devil.hytranslator.ui

sealed class OcrFlow {
    data object Hidden : OcrFlow()
    data object SourcePicker : OcrFlow()
    data object CameraActive : OcrFlow()
    data object Processing : OcrFlow()
    data class Result(val text: String) : OcrFlow()
    data class Error(val message: String) : OcrFlow()
}
