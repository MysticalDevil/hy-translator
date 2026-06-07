package org.devil.hytranslator.data.repository

import android.content.Context
import org.devil.hytranslator.domain.model.AiAsset
import java.io.File

internal class BundledAiAssetInstaller(
    private val context: Context,
    private val specs: Map<AiAsset, AiAssetSpec> = AiAssetSpecs.defaultSpecs,
) {
    fun installIfPresent(asset: AiAsset? = null) {
        requiredBundledAssetTargets(specs, asset).forEach { target ->
            if (!context.assets.assetExists(target.assetPath)) return@forEach

            val outputFile = File(context.filesDir, target.outputPath)
            if (outputFile.isFile && outputFile.length() >= target.minBytes) return@forEach

            outputFile.parentFile?.mkdirs()
            val tempFile = File(outputFile.parentFile, "${outputFile.name}.bundled")
            if (tempFile.exists()) {
                check(tempFile.delete()) {
                    "Failed to remove stale bundled AI asset temp file: ${tempFile.path}"
                }
            }
            context.assets.open(target.assetPath).use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            check(tempFile.length() >= target.minBytes) {
                "Bundled AI asset is smaller than required: ${target.assetPath}"
            }
            if (outputFile.exists()) {
                check(outputFile.delete()) {
                    "Failed to replace incomplete AI asset: ${outputFile.path}"
                }
            }
            check(tempFile.renameTo(outputFile)) {
                "Failed to install bundled AI asset: ${target.assetPath}"
            }
        }
    }

    internal companion object {
        const val ASSET_ROOT = "ai-assets"

        fun requiredBundledAssetTargets(
            specs: Map<AiAsset, AiAssetSpec>,
            asset: AiAsset? = null,
        ): List<BundledAiAssetTarget> =
            specs
                .filterKeys { candidate -> asset == null || candidate == asset }
                .values
                .flatMap { spec ->
                    spec.files.map { file ->
                        BundledAiAssetTarget(
                            assetPath = "$ASSET_ROOT/${spec.directoryName}/${file.name}",
                            outputPath = "$ASSET_ROOT/${spec.directoryName}/${file.name}",
                            minBytes = file.minBytes,
                        )
                    }
                }
    }
}

internal data class BundledAiAssetTarget(
    val assetPath: String,
    val outputPath: String,
    val minBytes: Long,
)
