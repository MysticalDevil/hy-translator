package org.devil.hytranslator.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.devil.hytranslator.R
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.AiAssetState
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.domain.repository.AiAssetRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class AiAssetRepositoryImpl(
    private val context: Context,
) : AiAssetRepository {
    private val asrState = MutableStateFlow<AiAssetState>(AiAssetState.NotDownloaded)
    private val ocrState = MutableStateFlow<AiAssetState>(AiAssetState.NotDownloaded)
    private val specs = AiAssetSpecs.defaultSpecs
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override fun state(asset: AiAsset): StateFlow<AiAssetState> =
        mutableState(asset)

    override fun refresh(asset: AiAsset) {
        mutableState(asset).value = if (hasRequiredFiles(asset)) {
            AiAssetState.Ready
        } else {
            AiAssetState.NotDownloaded
        }
    }

    override fun download(asset: AiAsset): Flow<DownloadProgress> = flow {
        val spec = specs.getValue(asset)
        val missingUrl = spec.files.firstOrNull { it.urlResId == null }
        if (missingUrl != null) {
            val message = "Download URL is not configured for ${missingUrl.name}"
            mutableState(asset).value = AiAssetState.Error(message)
            emit(DownloadProgress.Error(message))
            return@flow
        }

        val totalBytes = spec.files.sumOf { it.expectedBytes }
        var completedBytes = completedBytes(asset)
        mutableState(asset).value = AiAssetState.Downloading(
            DownloadProgress.Started(totalBytes, completedBytes),
        )
        emit(DownloadProgress.Started(totalBytes, completedBytes))

        for (fileSpec in spec.files) {
            coroutineContext.ensureActive()
            val file = File(assetDir(asset), fileSpec.name)
            if (file.isFile && file.length() >= fileSpec.minBytes) {
                continue
            }
            var failed = false
            val progress = downloadFile(asset, fileSpec)
            progress.collect { item ->
                when (item) {
                    is DownloadProgress.Started -> Unit
                    is DownloadProgress.Downloading -> {
                        val aggregate = DownloadProgress.Downloading(
                            downloaded = completedBytes + item.downloaded,
                            total = totalBytes,
                        )
                        mutableState(asset).value = AiAssetState.Downloading(aggregate)
                        emit(aggregate)
                    }
                    is DownloadProgress.Completed -> {
                        completedBytes += fileSpec.expectedBytes
                        val aggregate = DownloadProgress.Downloading(completedBytes, totalBytes)
                        mutableState(asset).value = AiAssetState.Downloading(aggregate)
                        emit(aggregate)
                    }
                    is DownloadProgress.Error -> {
                        mutableState(asset).value = AiAssetState.Error(item.message)
                        emit(item)
                        failed = true
                    }
                }
            }
            if (failed) return@flow
        }

        refresh(asset)
        if (state(asset).value is AiAssetState.Ready) {
            emit(DownloadProgress.Completed(localPath(asset)))
        } else {
            val message = "Downloaded files did not pass validation"
            mutableState(asset).value = AiAssetState.Error(message)
            emit(DownloadProgress.Error(message))
        }
    }.flowOn(Dispatchers.IO)

    override fun isReady(asset: AiAsset): Boolean {
        refresh(asset)
        return state(asset).value is AiAssetState.Ready
    }

    override fun localPath(asset: AiAsset): String =
        assetDir(asset).absolutePath

    private fun mutableState(asset: AiAsset): MutableStateFlow<AiAssetState> =
        when (asset) {
            AiAsset.AsrStreamingZipformer -> asrState
            AiAsset.OcrPpOcrV5Mobile -> ocrState
        }

    private fun hasRequiredFiles(asset: AiAsset): Boolean {
        val dir = assetDir(asset)
        return specs.getValue(asset).files.all { fileSpec ->
            val file = File(dir, fileSpec.name)
            file.isFile && file.length() >= fileSpec.minBytes
        }
    }

    private fun assetDir(asset: AiAsset): File =
        File(context.filesDir, "ai-assets/${specs.getValue(asset).directoryName}")

    private fun completedBytes(asset: AiAsset): Long {
        val dir = assetDir(asset)
        return specs.getValue(asset).files.sumOf { fileSpec ->
            val file = File(dir, fileSpec.name)
            if (file.isFile && file.length() >= fileSpec.minBytes) {
                fileSpec.expectedBytes
            } else {
                0L
            }
        }
    }

    private fun downloadFile(
        asset: AiAsset,
        fileSpec: AiAssetFileSpec,
    ): Flow<DownloadProgress> = flow {
        val url = context.getString(requireNotNull(fileSpec.urlResId))
        val dir = assetDir(asset).also { it.mkdirs() }
        val finalFile = File(dir, fileSpec.name)
        val tmpFile = File(dir, "${fileSpec.name}.tmp")

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
                emit(DownloadProgress.Error("HTTP ${response.code} while downloading ${fileSpec.name}"))
                return@flow
            }

            val responseBody = response.body
            val totalSize = if (existingSize > 0) {
                val contentRange = response.header("Content-Range")
                contentRange?.substringAfter("/")?.toLongOrNull()
                    ?: responseBody.contentLength().let { existingSize + it }
            } else {
                responseBody.contentLength()
            }

            FileOutputStream(tmpFile, existingSize > 0).use { output ->
                responseBody.byteStream().use { source ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var downloaded = existingSize
                    emit(DownloadProgress.Started(totalSize, existingSize))

                    while (source.read(buffer).also { bytesRead = it } != -1) {
                        coroutineContext.ensureActive()
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        emit(DownloadProgress.Downloading(downloaded, totalSize))
                    }
                }
            }

            if (tmpFile.length() < fileSpec.minBytes) {
                emit(DownloadProgress.Error("${fileSpec.name} is smaller than expected"))
                return@flow
            }

            if (finalFile.exists() && !finalFile.delete()) {
                emit(DownloadProgress.Error("Cannot replace ${fileSpec.name}"))
                return@flow
            }
            if (!tmpFile.renameTo(finalFile)) {
                emit(DownloadProgress.Error("Cannot finalize ${fileSpec.name}"))
                return@flow
            }
            emit(DownloadProgress.Completed(finalFile.absolutePath))
        }
    }.flowOn(Dispatchers.IO)

    private data class AiAssetSpec(
        val directoryName: String,
        val files: List<AiAssetFileSpec>,
    )

    private data class AiAssetFileSpec(
        val name: String,
        val minBytes: Long,
        val expectedBytes: Long = minBytes,
        val urlResId: Int? = null,
    )

    private object AiAssetSpecs {
        val defaultSpecs: Map<AiAsset, AiAssetSpec> = mapOf(
            AiAsset.AsrStreamingZipformer to AiAssetSpec(
                directoryName = "sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20",
                files = listOf(
                    AiAssetFileSpec(
                        name = "encoder-epoch-99-avg-1.onnx",
                        minBytes = 300_000_000L,
                        expectedBytes = 330_000_000L,
                        urlResId = R.string.url_asr_zipformer_encoder,
                    ),
                    AiAssetFileSpec(
                        name = "decoder-epoch-99-avg-1.onnx",
                        minBytes = 10_000_000L,
                        expectedBytes = 13_900_000L,
                        urlResId = R.string.url_asr_zipformer_decoder,
                    ),
                    AiAssetFileSpec(
                        name = "joiner-epoch-99-avg-1.onnx",
                        minBytes = 10_000_000L,
                        expectedBytes = 12_800_000L,
                        urlResId = R.string.url_asr_zipformer_joiner,
                    ),
                    AiAssetFileSpec(
                        name = "tokens.txt",
                        minBytes = 10_000L,
                        expectedBytes = 56_000L,
                        urlResId = R.string.url_asr_zipformer_tokens,
                    ),
                ),
            ),
            AiAsset.OcrPpOcrV5Mobile to AiAssetSpec(
                directoryName = "pp-ocrv5-mobile",
                files = listOf(
                    AiAssetFileSpec("PP-OCRv5_mobile_det.nb", minBytes = 1_000_000L),
                    AiAssetFileSpec("PP-OCRv5_mobile_rec.nb", minBytes = 1_000_000L),
                    AiAssetFileSpec("ppocr_keys_ocrv5.txt", minBytes = 10_000L),
                ),
            ),
        )

    }
}
