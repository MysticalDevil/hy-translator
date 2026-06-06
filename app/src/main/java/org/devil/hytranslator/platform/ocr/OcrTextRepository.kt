package org.devil.hytranslator.platform.ocr

import android.graphics.Bitmap
import android.net.Uri

interface OcrTextRepository : AutoCloseable {
    suspend fun recognize(bitmap: Bitmap): String

    suspend fun recognize(uri: Uri, decodeFailedMessage: String): String

    override fun close()
}
