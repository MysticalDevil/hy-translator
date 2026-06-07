package org.devil.hytranslator.data.repository

import org.devil.hytranslator.data.ModelOptions
import org.junit.Assert.assertEquals
import org.junit.Test

class BundledModelInstallerTest {
    @Test
    fun bundledModelTarget_usesDebugAssetsModelDirectory() {
        val model = ModelOptions.getByKey("Q4_K_M")

        val target = BundledModelInstaller.bundledModelTarget(model)

        assertEquals("models/Hy-MT2-1.8B-Q4_K_M.gguf", target.assetPath)
        assertEquals(target.assetPath, target.outputPath)
        assertEquals(100_000_000L, target.minBytes)
    }

    @Test
    fun assetExists_listsParentDirectoryAndMatchesFileName() {
        val queriedParents = mutableListOf<String>()

        val exists = assetExists("models/Hy-MT2-1.8B-Q4_K_M.gguf") { parent ->
            queriedParents += parent
            arrayOf("Hy-MT2-1.8B-Q4_K_M.gguf")
        }

        assertEquals(listOf("models"), queriedParents)
        assertEquals(true, exists)
    }

    @Test
    fun assetExists_returnsFalseWhenParentDirectoryDoesNotContainFile() {
        val exists = assetExists("models/Hy-MT2-1.8B-Q4_K_M.gguf") {
            arrayOf("other.gguf")
        }

        assertEquals(false, exists)
    }
}
