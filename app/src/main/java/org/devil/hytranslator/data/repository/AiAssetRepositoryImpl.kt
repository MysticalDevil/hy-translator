package org.devil.hytranslator.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.AiAssetState
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.domain.repository.AiAssetRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
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
        val url = context.getString(fileSpec.source.urlResId)
        val dir = assetDir(asset).also { it.mkdirs() }
        val finalFile = File(dir, fileSpec.name)
        val tmpFile = File(dir, "${fileSpec.name}.tmp")
        val downloadFile = when (fileSpec.source) {
            is AiAssetFileSource.Direct -> tmpFile
            is AiAssetFileSource.TarGz -> File(dir, "${fileSpec.name}.tar.gz.tmp")
        }

        var existingSize = downloadFile.takeIf { it.exists() }?.length() ?: 0L
        val requestBuilder = Request.Builder().url(url)
        if (existingSize > 0) {
            requestBuilder.header("Range", "bytes=$existingSize-")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (existingSize > 0 && response.code != 206) {
                if (!downloadFile.delete()) {
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

            FileOutputStream(downloadFile, existingSize > 0).use { output ->
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

            when (val source = fileSpec.source) {
                is AiAssetFileSource.Direct -> {
                    if (!moveDownloadedFile(downloadFile, finalFile, fileSpec)) {
                        emit(DownloadProgress.Error("Cannot finalize ${fileSpec.name}"))
                        return@flow
                    }
                }

                is AiAssetFileSource.TarGz -> {
                    val extracted = extractTarGzEntry(
                        archive = downloadFile,
                        entryName = source.entryName,
                        target = tmpFile,
                    )
                    if (!extracted) {
                        emit(DownloadProgress.Error("${source.entryName} was not found in archive"))
                        return@flow
                    }
                    if (!downloadFile.delete()) {
                        emit(DownloadProgress.Error("Cannot remove temporary archive for ${fileSpec.name}"))
                        return@flow
                    }
                    if (!moveDownloadedFile(tmpFile, finalFile, fileSpec)) {
                        emit(DownloadProgress.Error("Cannot finalize ${fileSpec.name}"))
                        return@flow
                    }
                }
            }

            emit(DownloadProgress.Completed(finalFile.absolutePath))
        }
    }.flowOn(Dispatchers.IO)

    private fun moveDownloadedFile(
        source: File,
        target: File,
        fileSpec: AiAssetFileSpec,
    ): Boolean {
        if (source.length() < fileSpec.minBytes) return false
        if (target.exists() && !target.delete()) return false
        return source.renameTo(target)
    }

    private fun extractTarGzEntry(
        archive: File,
        entryName: String,
        target: File,
    ): Boolean {
        GZIPInputStream(archive.inputStream()).use { gzip ->
            while (true) {
                val header = gzip.readTarHeader() ?: return false
                if (header.name.isBlank()) return false
                if (header.name.trimStart('.', '/') == entryName.trimStart('.', '/')) {
                    FileOutputStream(target).use { output ->
                        gzip.copyExactly(output, header.size)
                    }
                    return true
                }
                gzip.skipExactly(paddedTarSize(header.size))
            }
        }
    }
}

private data class TarHeader(
    val name: String,
    val size: Long,
)

private fun InputStream.readTarHeader(): TarHeader? {
    val header = ByteArray(TAR_BLOCK_SIZE)
    var offset = 0
    while (offset < header.size) {
        val read = read(header, offset, header.size - offset)
        if (read == -1) return null
        offset += read
    }
    if (header.all { it == 0.toByte() }) return null
    val name = header.stringField(start = 0, length = 100)
    val size = header.stringField(start = 124, length = 12)
        .trim()
        .toLongOrNull(radix = 8)
        ?: 0L
    return TarHeader(name = name, size = size)
}

private fun ByteArray.stringField(start: Int, length: Int): String {
    val end = (start until start + length)
        .firstOrNull { this[it] == 0.toByte() }
        ?: (start + length)
    return copyOfRange(start, end).toString(Charsets.UTF_8)
}

private fun InputStream.copyExactly(
    output: FileOutputStream,
    size: Long,
) {
    val buffer = ByteArray(8192)
    var remaining = size
    while (remaining > 0L) {
        val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (read == -1) error("Unexpected end of tar entry")
        output.write(buffer, 0, read)
        remaining -= read
    }
    skipExactly(paddedTarSize(size) - size)
}

private fun InputStream.skipExactly(size: Long) {
    var remaining = size
    while (remaining > 0L) {
        val skipped = skip(remaining)
        if (skipped <= 0L) {
            if (read() == -1) error("Unexpected end of tar archive")
            remaining -= 1L
        } else {
            remaining -= skipped
        }
    }
}

private fun paddedTarSize(size: Long): Long =
    ((size + TAR_BLOCK_SIZE - 1) / TAR_BLOCK_SIZE) * TAR_BLOCK_SIZE

private const val TAR_BLOCK_SIZE = 512
