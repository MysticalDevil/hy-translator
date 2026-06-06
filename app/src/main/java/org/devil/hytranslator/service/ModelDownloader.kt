package org.devil.hytranslator.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.devil.hytranslator.R
import org.devil.hytranslator.domain.model.DownloadProgress
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import java.util.concurrent.TimeUnit

class ModelDownloader(
    private val context: Context,
    private var filename: String = "Hy-MT2-1.8B-Q4_K_M.gguf",
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val modelDir: File
        get() = File(context.filesDir, "models").also { it.mkdirs() }

    private val modelFile: File
        get() = File(modelDir, filename)

    fun getModelPath(): String = modelFile.absolutePath

    fun isModelDownloaded(): Boolean = modelFile.exists() && modelFile.length() > 100_000_000L

    fun download(): Flow<DownloadProgress> = flow {
        val url = "${context.getString(R.string.url_hy_mt2_gguf_base)}$filename?download=true"
        val tmpFile = File(modelDir, "$filename.tmp")

        var existingSize = tmpFile.takeIf { it.exists() }?.length() ?: 0L

        val requestBuilder = Request.Builder().url(url)
        if (existingSize > 0) {
            requestBuilder.header("Range", "bytes=$existingSize-")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (existingSize > 0 && response.code != 206) {
                if (!tmpFile.delete()) {
                    emit(DownloadProgress.Error("Cannot restart partial download"))
                    return@flow
                }
                existingSize = 0L
            }

            if (!response.isSuccessful) {
                emit(DownloadProgress.Error("HTTP ${response.code}"))
                return@flow
            }

            val responseBody = response.body
            val totalSize = if (existingSize > 0) {
                val contentRange = response.header("Content-Range")
                if (contentRange != null) contentRange.substringAfter("/").toLongOrNull() ?: 0L
                else responseBody.contentLength().let { existingSize + it }
            } else {
                responseBody.contentLength()
            }

            val input = responseBody.byteStream()

            FileOutputStream(tmpFile, existingSize > 0).use { output ->
                input.use { source ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var downloaded = existingSize

                    emit(DownloadProgress.Started(totalSize, downloaded))

                    while (source.read(buffer).also { bytesRead = it } != -1) {
                        coroutineContext.ensureActive()
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        emit(DownloadProgress.Downloading(downloaded, totalSize))
                    }
                }
            }

            if (totalSize > 0 && tmpFile.length() != totalSize) {
                emit(DownloadProgress.Error("Incomplete download"))
                return@flow
            }

            if (!tmpFile.renameTo(modelFile)) {
                emit(DownloadProgress.Error("Cannot finalize download"))
                return@flow
            }
            emit(DownloadProgress.Completed(modelFile.absolutePath))
        }
    }.flowOn(Dispatchers.IO)

}
