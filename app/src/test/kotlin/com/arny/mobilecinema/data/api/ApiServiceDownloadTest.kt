package com.arny.mobilecinema.data.api

import com.arny.mobilecinema.data.models.DataResultWithProgress
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.concurrent.thread
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Loopback HTTP tests exercise the production Ktor client/stream, without a mock download flow. */
class ApiServiceDownloadTest {
    private fun tempDir(): File = Files.createTempDirectory(
        File("build/download-tests").apply { mkdirs() }.toPath(), "api-",
    ).toFile()

    @Test fun `success emits a terminal event and completes collection instead of waiting forever`() = runBlocking {
        val bytes = ByteArray(65536) { (it % 251).toByte() }
        val dir = tempDir()
        val server = server(bytes)
        val client = client()
        try {
            val file = File(dir, "archive.zip").apply { writeText("stale bytes from a previous attempt") }
            val events = withTimeoutOrNull(3_000) { ApiService(client).downloadFileWithProgress(file, server.url).toList() }
            assertNotNull(events, "Finished download must close its Flow so the service can start importing")
            assertTrue(events!!.last() is DataResultWithProgress.Success)
            assertEquals(1, events.count { it is DataResultWithProgress.Success })
            assertArrayEquals(bytes, file.readBytes(), "Retry must replace the target, not append to an old file")
            val progress = events.filterIsInstance<DataResultWithProgress.Progress<*>>()
                .map { it.result as com.arny.mobilecinema.data.models.DownloadFileResult }
            assertTrue(progress.isNotEmpty())
            assertEquals(100, progress.last().progress)
            assertTrue(progress.all { it.progress in 0..100 && it.size!! <= bytes.size })
            assertTrue(progress.zipWithNext().all { (a, b) -> a.size!! <= b.size!! })
        } finally { client.close(); server.close(); dir.deleteRecursively() }
    }

    @Test fun `chunked response without content length is downloaded to EOF`() = runBlocking {
        val bytes = "complete chunked archive".toByteArray()
        val dir = tempDir(); val server = server(bytes, chunked = true); val client = client()
        try {
            val file = File(dir, "archive.zip")
            val events = withTimeout(3_000) { ApiService(client).downloadFileWithProgress(file, server.url).toList() }
            assertTrue(events.last() is DataResultWithProgress.Success)
            assertArrayEquals(bytes, file.readBytes())
        } finally { client.close(); server.close(); dir.deleteRecursively() }
    }

    @Test fun `HTTP error is terminal and its HTML body is never a downloaded archive`() = runBlocking {
        val dir = tempDir(); val server = server("not an archive".toByteArray(), status = 404); val client = client()
        try {
            val file = File(dir, "archive.zip")
            val events = withTimeout(3_000) { ApiService(client).downloadFileWithProgress(file, server.url).toList() }
            assertEquals(1, events.size)
            assertTrue(events.single() is DataResultWithProgress.Error)
            assertFalse(file.exists())
        } finally { client.close(); server.close(); dir.deleteRecursively() }
    }

    @Test fun `truncated response never succeeds and removes incomplete output`() = runBlocking {
        val dir = tempDir(); val server = server(ByteArray(16), declaredSize = 4096); val client = client()
        try {
            val file = File(dir, "archive.zip")
            val events = withTimeout(3_000) { ApiService(client).downloadFileWithProgress(file, server.url).toList() }
            assertTrue(events.last() is DataResultWithProgress.Error)
            assertFalse(events.any { it is DataResultWithProgress.Success })
            assertFalse(file.exists())
        } finally { client.close(); server.close(); dir.deleteRecursively() }
    }

    @Test fun `cancelling collection of a partial download removes the partial file`() = runBlocking {
        val dir = tempDir(); val server = server(ByteArray(131072), slow = true); val client = client()
        try {
            val file = File(dir, "archive.zip")
            withTimeout(3_000) {
                ApiService(client).downloadFileWithProgress(file, server.url).first {
                    it is DataResultWithProgress.Progress && it.result.progress < 100
                }
            }
            assertFalse(file.exists())
        } finally { client.close(); server.close(); dir.deleteRecursively() }
    }

    private fun client() = HttpClient(OkHttp) { install(HttpTimeout) }

    private fun server(body: ByteArray, status: Int = 200, chunked: Boolean = false, declaredSize: Int = body.size, slow: Boolean = false): TestServer {
        val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val finishBody = CountDownLatch(1)
        val worker = thread(name = "download-test-http", isDaemon = true) {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 2000
                        val input = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                        while (true) {
                            val line = input.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val output = socket.getOutputStream()
                        val crlf = 13.toChar().toString() + 10.toChar()
                        val headers = listOf(
                            "HTTP/1.1 $status ${if (status == 200) "OK" else "Not Found"}",
                            if (chunked) "Transfer-Encoding: chunked" else "Content-Length: $declaredSize",
                            "Connection: close", "", "",
                        ).joinToString(crlf)
                        output.write(headers.toByteArray(Charsets.US_ASCII))
                        if (chunked) output.write((body.size.toString(16) + crlf).toByteArray(Charsets.US_ASCII))
                        if (slow) {
                            output.write(body, 0, 8192)
                            output.flush()
                            finishBody.await(5, TimeUnit.SECONDS)
                            output.write(body, 8192, body.size - 8192)
                        } else output.write(body)
                        if (chunked) output.write((crlf + "0" + crlf + crlf).toByteArray(Charsets.US_ASCII))
                        output.flush()
                    }
                } catch (_: java.io.IOException) {
                    // Truncated-body and cancellation tests intentionally close a response early.
                }
            }
        }
        return TestServer("http://127.0.0.1:${server.localPort}/archive") {
            finishBody.countDown(); server.close(); worker.join(1000)
        }
    }

    private class TestServer(val url: String, val stop: () -> Unit) {
        fun close() = stop()
    }
}
