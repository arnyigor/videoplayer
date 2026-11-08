package com.arny.mobilecinema.data

import com.arny.mobilecinema.data.models.DataResultWithProgress
import com.arny.mobilecinema.domain.interactors.jsoupupdate.JsoupUpdateInteractor
import com.arny.mobilecinema.presentation.DeviceGuard
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors

/**
 * A card that disappeared from the site answers 404. The parse used to emit progress only and never
 * finish, so the foreground service and the details spinner hung forever. The flow must terminate.
 */
class Update404FlowTest {
    private lateinit var server: ServerSocket
    // Пул: accept-цикл не должен занимать поток, который обслуживает соединение.
    private val executor = Executors.newCachedThreadPool()
    private val requests = java.util.concurrent.atomic.AtomicInteger()

    @Before fun serve404() {
        DeviceGuard.requireApprovedDevice()
        server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        executor.execute {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                executor.execute {
                    socket.use {
                        requests.incrementAndGet()
                        it.getInputStream().bufferedReader().readLine()
                        val body = "<html><body><h1>Ошибка 404</h1></body></html>".toByteArray()
                        it.getOutputStream().apply {
                            write(("HTTP/1.1 404 Not Found\r\nContent-Type: text/html; charset=utf-8\r\n" +
                                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray())
                            write(body)
                            flush()
                        }
                    }
                }
            }
        }
    }

    @After fun stop() {
        if (::server.isInitialized) server.close()
        executor.shutdownNow()
    }

    @Test fun missingCardFinishesWithFailureInsteadOfHanging() = runBlocking {
        val interactor = GlobalContext.get().get<JsoupUpdateInteractor>()
        val url = "http://127.0.0.1:${server.localPort}/films/999999"
        var terminal: Boolean? = null
        val emitted = mutableListOf<String>()
        withTimeout(20_000) {
            interactor.getPageData(url, true) { data ->
                when (data) {
                    is DataResultWithProgress.Progress -> {
                        emitted += data.result.progress.entries.joinToString(";") { "${it.key}=${it.value}" } +
                            " complete=${data.result.complete} success=${data.result.success}"
                        if (data.result.complete) terminal = data.result.success
                    }
                    is DataResultWithProgress.Success -> terminal = true
                    is DataResultWithProgress.Error -> {
                        emitted += "error=${data.throwable}"
                        terminal = false
                    }
                }
            }
        }
        assertNotNull("404 did not terminate the parse (server requests=${requests.get()}, emitted=${emitted})", terminal)
        assertFalse("404 must not be reported as success", terminal!!)
        val text = emitted.joinToString(" | ")
        assertTrue("Expected a 404 explanation, got: $text", text.contains("404"))
        assertTrue("Garbage page title leaked into the status: $text", !text.contains("Ошибка 404"))
    }
}
