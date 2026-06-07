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
import java.io.IOException
import kotlin.coroutines.coroutineContext
import java.util.concurrent.TimeUnit

class ModelDownloader private constructor(
    private val modelDirProvider: () -> File,
    private val baseUrl: String,
    private var filename: String,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
) {
    constructor(
        context: Context,
        filename: String = "Hy-MT2-1.8B-Q4_K_M.gguf",
    ) : this(
        modelDirProvider = { File(context.filesDir, "models").also { it.mkdirs() } },
        baseUrl = context.getString(R.string.url_hy_mt2_gguf_base),
        filename = filename,
    )

    internal constructor(
        modelDir: File,
        baseUrl: String,
        filename: String,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    ) : this(
        modelDirProvider = { modelDir.also { it.mkdirs() } },
        baseUrl = baseUrl,
        filename = filename,
        maxAttempts = maxAttempts,
    )

    private val client = defaultClient()

    private val modelDir: File
        get() = modelDirProvider()

    private val modelFile: File
        get() = File(modelDir, filename)

    fun getModelPath(): String = modelFile.absolutePath

    fun isModelDownloaded(): Boolean = modelFile.exists() && modelFile.length() > 100_000_000L

    fun download(): Flow<DownloadProgress> = flow {
        val url = "$baseUrl$filename?download=true"
        val tmpFile = File(modelDir, "$filename.tmp")

        var lastError: DownloadProgress.Error? = null
        repeat(maxAttempts) { attempt ->
            var existingSize = tmpFile.takeIf { it.exists() }?.length() ?: 0L

            val requestBuilder = Request.Builder().url(url)
            if (existingSize > 0) {
                requestBuilder.header("Range", "bytes=$existingSize-")
            }

            try {
                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (existingSize > 0 && response.code != 206) {
                        if (!tmpFile.delete()) {
                            emit(DownloadProgress.Error("Cannot restart partial download"))
                            return@flow
                        }
                        existingSize = 0L
                    }

                    if (!response.isSuccessful) {
                        lastError = DownloadProgress.Error("HTTP ${response.code}")
                        if (response.code.isRetryableHttpStatus() && attempt.hasAttemptsRemaining()) {
                            return@repeat
                        }
                        emit(lastError ?: DownloadProgress.Error("HTTP ${response.code}"))
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
                        lastError = DownloadProgress.Error("Incomplete download")
                        if (attempt.hasAttemptsRemaining()) {
                            return@repeat
                        }
                        emit(lastError ?: DownloadProgress.Error("Incomplete download"))
                        return@flow
                    }

                    coroutineContext.ensureActive()
                    if (!tmpFile.renameTo(modelFile)) {
                        emit(DownloadProgress.Error("Cannot finalize download"))
                        return@flow
                    }
                    emit(DownloadProgress.Completed(modelFile.absolutePath))
                    return@flow
                }
            } catch (error: IOException) {
                lastError = DownloadProgress.Error(error.message ?: "Network error")
                if (!attempt.hasAttemptsRemaining()) {
                    emit(lastError ?: DownloadProgress.Error(error.message ?: "Network error"))
                    return@flow
                }
            }
        }
        emit(lastError ?: DownloadProgress.Error("Download failed"))
    }.flowOn(Dispatchers.IO)

    private fun Int.hasAttemptsRemaining(): Boolean =
        this < maxAttempts - 1

    private companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()

        fun Int.isRetryableHttpStatus(): Boolean = this in 500..599
    }
}
