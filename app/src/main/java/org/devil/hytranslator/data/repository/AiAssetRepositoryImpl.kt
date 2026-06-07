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
import java.io.File
import kotlin.coroutines.coroutineContext

class AiAssetRepositoryImpl(
    private val context: Context,
) : AiAssetRepository {
    private val asrState = MutableStateFlow<AiAssetState>(AiAssetState.NotDownloaded)
    private val ocrState = MutableStateFlow<AiAssetState>(AiAssetState.NotDownloaded)
    private val specs = AiAssetSpecs.defaultSpecs
    private val fileDownloader = AiAssetFileDownloader()
    private val bundledAiAssetInstaller = BundledAiAssetInstaller(context)

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
        bundledAiAssetInstaller.installIfPresent(asset)
        if (hasRequiredFiles(asset)) {
            refresh(asset)
            emit(DownloadProgress.Completed(localPath(asset)))
            return@flow
        }

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
            val progress = fileDownloader.downloadFile(
                dir = assetDir(asset),
                fileSpec = fileSpec,
                url = context.getString(fileSpec.source.urlResId),
            )
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

}
