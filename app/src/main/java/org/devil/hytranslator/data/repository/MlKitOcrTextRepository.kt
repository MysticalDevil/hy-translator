package org.devil.hytranslator.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.devil.hytranslator.platform.ocr.OcrTextRepository
import org.devil.hytranslator.service.OcrEngine

class MlKitOcrTextRepository(
    private val context: Context,
) : OcrTextRepository {
    private val ocrEngine = OcrEngine()

    override suspend fun recognize(bitmap: Bitmap): String =
        ocrEngine.recognize(bitmap)

    override suspend fun recognize(uri: Uri, decodeFailedMessage: String): String {
        val bitmap = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val raw = BitmapFactory.decodeStream(inputStream)
                raw ?: throw OcrProcessingException(decodeFailedMessage)
            }
                ?: throw OcrProcessingException(decodeFailedMessage)
        }
        val corrected = withContext(Dispatchers.IO) {
            val orientation = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: return@withContext bitmap
            rotateBitmap(bitmap, orientation)
        }
        return ocrEngine.recognize(corrected)
    }

    override fun close() {
        ocrEngine.close()
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

class OcrProcessingException(message: String) : Exception(message)
