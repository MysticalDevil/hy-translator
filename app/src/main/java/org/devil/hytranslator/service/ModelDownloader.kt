package org.devil.hytranslator.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ModelDownloader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val modelDir: File
        get() = File(context.filesDir, "models").also { it.mkdirs() }

    private val modelFile: File
        get() = File(modelDir, MODEL_FILENAME)

    fun getModelPath(): String = modelFile.absolutePath

    fun isModelDownloaded(): Boolean = modelFile.exists() && modelFile.length() > 100_000_000L

    fun getModelSize(): Long = if (modelFile.exists()) modelFile.length() else 0L

    fun download(): Flow<DownloadProgress> = flow {
        val url = "${HF_BASE_URL}${MODEL_FILENAME}?download=true"
        val tmpFile = File(modelDir, "$MODEL_FILENAME.tmp")

        val existingSize = tmpFile.takeIf { it.exists() }?.length() ?: 0L

        val requestBuilder = Request.Builder().url(url)
        if (existingSize > 0) {
            requestBuilder.header("Range", "bytes=$existingSize-")
        }

        val response = client.newCall(requestBuilder.build()).execute()

        if (!response.isSuccessful && response.code != 206) {
            emit(DownloadProgress.Error("HTTP ${response.code}"))
            return@flow
        }

        val totalSize = if (existingSize > 0) {
            val contentRange = response.header("Content-Range")
            if (contentRange != null) contentRange.substringAfter("/").toLongOrNull() ?: 0L
            else response.body?.contentLength()?.let { existingSize + it } ?: 0L
        } else {
            response.body?.contentLength() ?: 0L
        }

        val input = response.body?.byteStream() ?: run {
            emit(DownloadProgress.Error("Cannot read response body"))
            return@flow
        }

        val output = FileOutputStream(tmpFile, existingSize > 0)
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var downloaded = existingSize

        emit(DownloadProgress.Started(totalSize, downloaded))

        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            downloaded += bytesRead
            emit(DownloadProgress.Downloading(downloaded, totalSize))
        }

        output.close()
        input.close()

        tmpFile.renameTo(modelFile)
        emit(DownloadProgress.Completed(modelFile.absolutePath))
    }.flowOn(Dispatchers.IO)

    companion object {
        const val MODEL_FILENAME = "Hy-MT2-1.8B-Q4_K_M.gguf"
        const val HF_BASE_URL = "https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF/resolve/main/"
    }
}

sealed class DownloadProgress {
    data class Started(val total: Long, val existing: Long) : DownloadProgress()
    data class Downloading(val downloaded: Long, val total: Long) : DownloadProgress()
    data class Completed(val path: String) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}
