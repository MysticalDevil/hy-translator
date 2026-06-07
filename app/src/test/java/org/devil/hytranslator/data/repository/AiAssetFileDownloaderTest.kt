package org.devil.hytranslator.data.repository

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.devil.hytranslator.domain.model.DownloadProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPOutputStream

class AiAssetFileDownloaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun downloadFile_directSourceCompletes() = runTest {
        val fileSpec = AiAssetFileSpec(
            name = "tokens.txt",
            minBytes = DIRECT_TEXT.length.toLong(),
            source = AiAssetFileSource.Direct(urlResId = 0),
        )
        LocalHttpServer { exchange ->
            exchange.respond(status = 200, body = DIRECT_TEXT.toByteArray())
        }.use { server ->
            val dir = temporaryFolder.newFolder("asset")

            val progress = AiAssetFileDownloader()
                .downloadFile(dir = dir, fileSpec = fileSpec, url = server.baseUrl)
                .toList()

            assertTrue(progress.first() is DownloadProgress.Started)
            assertEquals(
                DownloadProgress.Completed(java.io.File(dir, fileSpec.name).absolutePath),
                progress.last(),
            )
            assertEquals(DIRECT_TEXT, java.io.File(dir, fileSpec.name).readText())
        }
    }

    @Test
    fun downloadFile_tarGzSourceExtractsConfiguredEntry() = runTest {
        val fileSpec = AiAssetFileSpec(
            name = "PP-OCRv5_mobile_rec.nb",
            minBytes = TAR_ENTRY_TEXT.length.toLong(),
            source = AiAssetFileSource.TarGz(
                urlResId = 0,
                entryName = "nested/PP-OCRv5_mobile_rec.nb",
            ),
        )
        LocalHttpServer { exchange ->
            exchange.respond(
                status = 200,
                body = tarGz(entryName = "nested/PP-OCRv5_mobile_rec.nb", body = TAR_ENTRY_TEXT),
            )
        }.use { server ->
            val dir = temporaryFolder.newFolder("asset")

            val progress = AiAssetFileDownloader()
                .downloadFile(dir = dir, fileSpec = fileSpec, url = server.baseUrl)
                .toList()

            assertEquals(
                DownloadProgress.Completed(java.io.File(dir, fileSpec.name).absolutePath),
                progress.last(),
            )
            assertEquals(TAR_ENTRY_TEXT, java.io.File(dir, fileSpec.name).readText())
        }
    }

    @Test
    fun downloadFile_resumesDirectSourceWithRangeRequest() = runTest {
        val observedRange = AtomicReference<String?>()
        val fileSpec = AiAssetFileSpec(
            name = "encoder.onnx",
            minBytes = DIRECT_TEXT.length.toLong(),
            source = AiAssetFileSource.Direct(urlResId = 0),
        )
        val dir = temporaryFolder.newFolder("asset")
        java.io.File(dir, "${fileSpec.name}.tmp").writeText("abc")
        LocalHttpServer { exchange ->
            observedRange.set(exchange.requestHeaders.getFirst("Range"))
            exchange.responseHeaders.add("Content-Range", "bytes 3-5/6")
            exchange.respond(status = 206, body = "def".toByteArray())
        }.use { server ->
            val progress = AiAssetFileDownloader()
                .downloadFile(dir = dir, fileSpec = fileSpec, url = server.baseUrl)
                .toList()

            assertEquals("bytes=3-", observedRange.get())
            assertEquals(
                DownloadProgress.Completed(java.io.File(dir, fileSpec.name).absolutePath),
                progress.last(),
            )
            assertEquals(DIRECT_TEXT, java.io.File(dir, fileSpec.name).readText())
        }
    }

    private class LocalHttpServer(
        handler: (HttpExchange) -> Unit,
    ) : Closeable {
        private val executor = Executors.newSingleThreadExecutor()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        val baseUrl: String
            get() = "http://127.0.0.1:${server.address.port}/"

        init {
            server.createContext("/") { exchange -> handler(exchange) }
            server.executor = executor
            server.start()
        }

        override fun close() {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    private companion object {
        const val DIRECT_TEXT = "abcdef"
        const val TAR_ENTRY_TEXT = "ocr-model"
        const val TAR_BLOCK_SIZE = 512

        fun HttpExchange.respond(status: Int, body: ByteArray) {
            sendResponseHeaders(status, body.size.toLong())
            responseBody.use { output -> output.write(body) }
        }

        fun tarGz(entryName: String, body: String): ByteArray {
            val tar = ByteArrayOutputStream()
            val bodyBytes = body.toByteArray()
            tar.write(tarHeader(entryName, bodyBytes.size))
            tar.write(bodyBytes)
            tar.write(ByteArray(paddedTarSize(bodyBytes.size) - bodyBytes.size))
            tar.write(ByteArray(TAR_BLOCK_SIZE * 2))

            val gzipBytes = ByteArrayOutputStream()
            GZIPOutputStream(gzipBytes).use { gzip -> gzip.write(tar.toByteArray()) }
            return gzipBytes.toByteArray()
        }

        fun tarHeader(name: String, size: Int): ByteArray {
            val header = ByteArray(TAR_BLOCK_SIZE)
            name.toByteArray().copyInto(header, destinationOffset = 0)
            "0000777".toByteArray().copyInto(header, destinationOffset = 100)
            "0000000".toByteArray().copyInto(header, destinationOffset = 108)
            "0000000".toByteArray().copyInto(header, destinationOffset = 116)
            size.toString(8).padStart(11, '0').toByteArray()
                .copyInto(header, destinationOffset = 124)
            header[135] = 0
            "00000000000".toByteArray().copyInto(header, destinationOffset = 136)
            header[156] = '0'.code.toByte()
            for (index in 148 until 156) header[index] = ' '.code.toByte()
            val checksum = header.sumOf { it.toUByte().toInt() }
            checksum.toString(8).padStart(6, '0').toByteArray()
                .copyInto(header, destinationOffset = 148)
            header[154] = 0
            header[155] = ' '.code.toByte()
            return header
        }

        fun paddedTarSize(size: Int): Int =
            ((size + TAR_BLOCK_SIZE - 1) / TAR_BLOCK_SIZE) * TAR_BLOCK_SIZE
    }
}
