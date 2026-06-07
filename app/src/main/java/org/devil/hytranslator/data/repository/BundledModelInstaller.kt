package org.devil.hytranslator.data.repository

import android.content.Context
import android.content.res.AssetManager
import org.devil.hytranslator.domain.model.ModelOption
import java.io.File

internal class BundledModelInstaller(
    private val context: Context,
) {
    fun installIfPresent(model: ModelOption) {
        val target = bundledModelTarget(model)
        if (!context.assets.assetExists(target.assetPath)) return

        val outputFile = File(context.filesDir, target.outputPath)
        if (outputFile.isFile && outputFile.length() > MIN_MODEL_BYTES) return

        outputFile.parentFile?.mkdirs()
        val tempFile = File(outputFile.parentFile, "${outputFile.name}.bundled")
        if (tempFile.exists()) {
            check(tempFile.delete()) {
                "Failed to remove stale bundled model temp file: ${tempFile.path}"
            }
        }
        context.assets.open(target.assetPath).use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
        check(tempFile.length() > MIN_MODEL_BYTES) {
            "Bundled translation model is smaller than required: ${target.assetPath}"
        }
        if (outputFile.exists()) {
            check(outputFile.delete()) {
                "Failed to replace incomplete translation model: ${outputFile.path}"
            }
        }
        check(tempFile.renameTo(outputFile)) {
            "Failed to install bundled translation model: ${target.assetPath}"
        }
    }

    companion object {
        const val ASSET_ROOT = "models"
        const val MIN_MODEL_BYTES = 100_000_000L

        fun bundledModelTarget(model: ModelOption): BundledModelTarget =
            BundledModelTarget(
                assetPath = "$ASSET_ROOT/${model.filename}",
                outputPath = "$ASSET_ROOT/${model.filename}",
                minBytes = MIN_MODEL_BYTES,
            )
    }
}

internal data class BundledModelTarget(
    val assetPath: String,
    val outputPath: String,
    val minBytes: Long,
)

internal fun AssetManager.assetExists(path: String): Boolean {
    return assetExists(path) { parent -> list(parent) }
}

internal fun assetExists(path: String, listAssets: (String) -> Array<String>?): Boolean {
    val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
    val name = path.substringAfterLast('/')
    return listAssets(parent).orEmpty().contains(name)
}
