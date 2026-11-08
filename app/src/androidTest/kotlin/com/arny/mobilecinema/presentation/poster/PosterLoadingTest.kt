package com.arny.mobilecinema.presentation.poster

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.widget.ImageView
import androidx.test.platform.app.InstrumentationRegistry
import com.arny.mobilecinema.presentation.DeviceGuard
import com.arny.mobilecinema.presentation.utils.clearPoster
import com.arny.mobilecinema.presentation.utils.loadPoster
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/** Offline image origin which rejects non-browser requests and unsupported-format negotiation. */
internal class PosterServer : AutoCloseable {
    private val server = ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"))
    private val executor = Executors.newCachedThreadPool()
    val origin = "http://127.0.0.1:${server.localPort}"
    val requests = CopyOnWriteArrayList<String>()
    val headers = CopyOnWriteArrayList<Map<String, String>>()
    val red = Color.rgb(219, 68, 55)
    val green = Color.rgb(30, 190, 60)

    init {
        executor.execute {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                executor.execute {
                    try {
                        socket.use {
                            val reader = it.getInputStream().bufferedReader()
                            val path = reader.readLine()?.split(' ')?.getOrNull(1) ?: return@execute
                            val values = mutableMapOf<String, String>()
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                values[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                            }
                            requests += path
                            headers += values
                            if (path.contains("slow")) Thread.sleep(400)
                            val allowed = values["user-agent"].orEmpty().contains("Mozilla/") &&
                                !values["accept"].orEmpty().contains("image/avif")
                            val code = if (!allowed) 403 else if (path.startsWith("/missing")) 404 else 200
                            val bytes = if (code == 200) png(if (path.contains("green")) green else red) else ByteArray(0)
                            it.getOutputStream().apply {
                                write(("HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\n" +
                                    "Content-Type: image/png\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
                                write(bytes)
                                flush()
                            }
                        }
                    } catch (_: Exception) { /* A recycled target may cancel an in-flight socket. */ }
                }
            }
        }
    }

    private fun png(color: Int): ByteArray = ByteArrayOutputStream().use { output ->
        val bitmap = Bitmap.createBitmap(24, 36, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        output.toByteArray()
    }

    override fun close() { server.close(); executor.shutdownNow() }
}

internal fun bitmapColor(drawable: Drawable?): Int? = when (drawable) {
    is BitmapDrawable -> {
        val bitmap = drawable.bitmap
        if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)?.let { copy ->
                val color = copy.getPixel(copy.width / 2, copy.height / 2)
                copy.recycle()
                color
            }
        } else bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
    }
    is LayerDrawable -> bitmapColor(drawable.getDrawable(drawable.numberOfLayers - 1))
    else -> null
}

class PosterLoadingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var server: PosterServer
    private lateinit var image: ImageView

    @Before fun setup() {
        DeviceGuard.requireApprovedDevice()
        assertTrue("Use isolated fixture APK, not the installed user application",
            instrumentation.targetContext.packageName.endsWith(".perfdebug") ||
                instrumentation.targetContext.packageName.endsWith(".tvdebug"))
        server = PosterServer()
        instrumentation.runOnMainSync {
            image = ImageView(instrumentation.targetContext).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(120, 180)
                layout(0, 0, 120, 180)
            }
        }
    }

    @After fun cleanup() {
        if (::image.isInitialized) instrumentation.runOnMainSync { image.clearPoster() }
        if (::server.isInitialized) server.close()
    }

    private fun awaitColor(color: Int) {
        val deadline = System.currentTimeMillis() + 12_000
        while (System.currentTimeMillis() < deadline) {
            var actual: Int? = null
            instrumentation.runOnMainSync { actual = bitmapColor(image.drawable) }
            if (actual == color) return
            Thread.sleep(50)
        }
        fail("Poster did not decode/bind, requests=${server.requests}")
    }

    @Test fun relativePosterUsesBrowserHeadersAndDecodesIntoTheView() {
        instrumentation.runOnMainSync { image.loadPoster("/poster.png", server.origin + "/films/1") }
        awaitColor(server.red)
        assertEquals(listOf("/poster.png"), server.requests.toList())
        assertTrue(server.headers.single().getValue("user-agent").contains("Mozilla/"))
        assertFalse(server.headers.single().getValue("accept").contains("image/avif"))
    }

    @Test fun cachedPosterIsAvailableWithoutTheNetwork() {
        instrumentation.runOnMainSync { image.loadPoster("/poster.png", server.origin) }
        awaitColor(server.red)
        val count = server.requests.size
        server.close()
        instrumentation.runOnMainSync {
            image.clearPoster()
            image.loadPoster("/poster.png", server.origin)
        }
        awaitColor(server.red)
        assertEquals("Cached image unexpectedly fetched again", count, server.requests.size)
    }

    @Test fun replacingSlowPosterCannotOverwriteTheNewCard() {
        instrumentation.runOnMainSync {
            image.loadPoster("/slow-red.png", server.origin)
            image.loadPoster("/green.png", server.origin)
        }
        awaitColor(server.green)
        Thread.sleep(700)
        instrumentation.runOnMainSync { assertEquals(server.green, bitmapColor(image.drawable)) }
    }

    @Test fun clearingAPosterCancelsOldImageAndShowsPlaceholder() {
        instrumentation.runOnMainSync { image.loadPoster("/poster.png", server.origin) }
        awaitColor(server.red)
        instrumentation.runOnMainSync {
            image.loadPoster("", server.origin)
            assertNotNull(image.drawable)
            assertNull("Old bitmap leaked into a card without an image", bitmapColor(image.drawable))
        }
    }

    @Test fun failedOldOriginRetriesCurrentOriginOnce() {
        val unavailablePort = ServerSocket(0).use { it.localPort }
        instrumentation.runOnMainSync {
            image.loadPoster("http://127.0.0.1:$unavailablePort/poster.png", server.origin)
        }
        awaitColor(server.red)
        assertEquals(listOf("/poster.png"), server.requests.toList())
    }

    @Test fun missingPosterHasPlaceholderAndNoRetryLoop() {
        instrumentation.runOnMainSync { image.loadPoster("/missing.png", server.origin) }
        val deadline = System.currentTimeMillis() + 5_000
        while (server.requests.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertEquals(listOf("/missing.png"), server.requests.toList())
        Thread.sleep(500)
        instrumentation.runOnMainSync { assertNotNull(image.drawable); assertNull(bitmapColor(image.drawable)) }
        assertEquals(1, server.requests.size)
    }
}
