package org.devil.hytranslator.service

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
import java.io.Closeable
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class ModelDownloaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun download_completesFromHttpSource() = runTest {
        LocalHttpServer { exchange ->
            exchange.respond(status = 200, body = MODEL_BYTES)
        }.use { server ->
            val downloader = ModelDownloader(
                modelDir = temporaryFolder.newFolder("models"),
                baseUrl = server.baseUrl,
                filename = MODEL_NAME,
            )

            val progress = downloader.download().toList()

            assertTrue(progress.first() is DownloadProgress.Started)
            assertEquals(
                DownloadProgress.Completed(downloader.getModelPath()),
                progress.last(),
            )
            assertEquals(MODEL_TEXT, java.io.File(downloader.getModelPath()).readText())
        }
    }

    @Test
    fun download_emitsHttpError() = runTest {
        LocalHttpServer { exchange ->
            exchange.respond(status = 503, body = "unavailable".toByteArray())
        }.use { server ->
            val downloader = ModelDownloader(
                modelDir = temporaryFolder.newFolder("models"),
                baseUrl = server.baseUrl,
                filename = MODEL_NAME,
            )

            val progress = downloader.download().toList()

            assertEquals(DownloadProgress.Error("HTTP 503"), progress.single())
        }
    }

    @Test
    fun download_resumesPartialFileWithRangeRequest() = runTest {
        val observedRange = AtomicReference<String?>()
        val modelDir = temporaryFolder.newFolder("models")
        java.io.File(modelDir, "$MODEL_NAME.tmp").writeText("abc")
        LocalHttpServer { exchange ->
            observedRange.set(exchange.requestHeaders.getFirst("Range"))
            exchange.responseHeaders.add("Content-Range", "bytes 3-5/6")
            exchange.respond(status = 206, body = "def".toByteArray())
        }.use { server ->
            val downloader = ModelDownloader(
                modelDir = modelDir,
                baseUrl = server.baseUrl,
                filename = MODEL_NAME,
            )

            val progress = downloader.download().toList()

            assertEquals("bytes=3-", observedRange.get())
            assertEquals(
                DownloadProgress.Completed(downloader.getModelPath()),
                progress.last(),
            )
            assertEquals(MODEL_TEXT, java.io.File(downloader.getModelPath()).readText())
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
        const val MODEL_NAME = "model.gguf"
        const val MODEL_TEXT = "abcdef"
        val MODEL_BYTES = MODEL_TEXT.toByteArray()

        fun HttpExchange.respond(status: Int, body: ByteArray) {
            sendResponseHeaders(status, body.size.toLong())
            responseBody.use { output -> output.write(body) }
        }
    }
}
