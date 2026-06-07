package org.devil.hytranslator.service

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.devil.hytranslator.domain.model.DownloadProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
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

    @Test
    fun download_retriesServerErrorAndCompletes() = runTest {
        val requestCount = AtomicInteger()
        LocalHttpServer { exchange ->
            if (requestCount.incrementAndGet() == 1) {
                exchange.respond(status = 503, body = "unavailable".toByteArray())
            } else {
                exchange.respond(status = 200, body = MODEL_BYTES)
            }
        }.use { server ->
            val downloader = ModelDownloader(
                modelDir = temporaryFolder.newFolder("models"),
                baseUrl = server.baseUrl,
                filename = MODEL_NAME,
            )

            val progress = downloader.download().toList()

            assertEquals(2, requestCount.get())
            assertEquals(
                DownloadProgress.Completed(downloader.getModelPath()),
                progress.last(),
            )
            assertEquals(MODEL_TEXT, java.io.File(downloader.getModelPath()).readText())
        }
    }

    @Test
    fun download_retriesInterruptedBodyWithRangeRequest() = runTest {
        val requestCount = AtomicInteger()
        val observedRange = AtomicReference<String?>()
        LocalHttpServer { exchange ->
            if (requestCount.incrementAndGet() == 1) {
                exchange.respondAndCloseEarly(
                    status = 200,
                    declaredSize = MODEL_BYTES.size.toLong(),
                    body = "abc".toByteArray(),
                )
            } else {
                observedRange.set(exchange.requestHeaders.getFirst("Range"))
                exchange.responseHeaders.add("Content-Range", "bytes 3-5/6")
                exchange.respond(status = 206, body = "def".toByteArray())
            }
        }.use { server ->
            val downloader = ModelDownloader(
                modelDir = temporaryFolder.newFolder("models"),
                baseUrl = server.baseUrl,
                filename = MODEL_NAME,
            )

            val progress = downloader.download().toList()

            assertEquals(2, requestCount.get())
            assertEquals("bytes=3-", observedRange.get())
            assertEquals(
                DownloadProgress.Completed(downloader.getModelPath()),
                progress.last(),
            )
            assertEquals(MODEL_TEXT, java.io.File(downloader.getModelPath()).readText())
        }
    }

    @Test
    fun download_whenCollectorCancels_doesNotFinalizePartialFile() = runTest {
        LocalHttpServer { exchange ->
            exchange.respondSlowly(status = 200, body = ByteArray(PARTIAL_CANCEL_BYTES) { 1 })
        }.use { server ->
            val modelDir = temporaryFolder.newFolder("models")
            val downloader = ModelDownloader(
                modelDir = modelDir,
                baseUrl = server.baseUrl,
                filename = MODEL_NAME,
            )

            val progress = downloader.download().take(2).toList()

            assertTrue(progress.first() is DownloadProgress.Started)
            assertTrue(progress.last() is DownloadProgress.Downloading)
            assertFalse(java.io.File(downloader.getModelPath()).exists())
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
        const val PARTIAL_CANCEL_BYTES = 32 * 1024
        val MODEL_BYTES = MODEL_TEXT.toByteArray()

        fun HttpExchange.respond(status: Int, body: ByteArray) {
            sendResponseHeaders(status, body.size.toLong())
            responseBody.use { output -> output.write(body) }
        }

        fun HttpExchange.respondSlowly(status: Int, body: ByteArray) {
            sendResponseHeaders(status, body.size.toLong())
            responseBody.use { output ->
                output.write(body, 0, SLOW_FIRST_CHUNK_BYTES)
                output.flush()
                Thread.sleep(SLOW_RESPONSE_DELAY_MS)
                try {
                    output.write(body, SLOW_FIRST_CHUNK_BYTES, body.size - SLOW_FIRST_CHUNK_BYTES)
                } catch (_: IOException) {
                    // The cancellation test closes the client after the first chunk.
                }
            }
        }

        fun HttpExchange.respondAndCloseEarly(
            status: Int,
            declaredSize: Long,
            body: ByteArray,
        ) {
            sendResponseHeaders(status, declaredSize)
            responseBody.use { output ->
                output.write(body)
                output.flush()
            }
        }

        const val SLOW_FIRST_CHUNK_BYTES = 8 * 1024
        const val SLOW_RESPONSE_DELAY_MS = 500L
    }
}
