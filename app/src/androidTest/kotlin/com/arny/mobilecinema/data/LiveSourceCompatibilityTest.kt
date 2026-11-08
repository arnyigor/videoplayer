package com.arny.mobilecinema.data

import com.arny.mobilecinema.data.utils.posterUrlCandidates
import androidx.test.platform.app.InstrumentationRegistry
import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.data.api.KtorClient
import com.arny.mobilecinema.data.network.jsoup.JsoupService
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.interactors.jsoupupdate.getBestFilmCinemaLink
import com.arny.mobilecinema.presentation.DeviceGuard
import io.ktor.client.call.body
import io.ktor.utils.io.ByteReadChannel
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.plugins.timeout
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.net.URI
import java.util.concurrent.TimeUnit

/** Opt-in real-network checks. Read-only: no catalog import or persistent file writes. */
class LiveSourceCompatibilityTest {
    private val jsoup = JsoupService.getInstance()

    @Before
    fun requireOptIn() {
        DeviceGuard.requireApprovedDevice()
        assumeTrue("Requires -e liveNetwork true", InstrumentationRegistry.getArguments().getString("liveNetwork") == "true")
    }

    @Test
    fun catalogArchiveIsReachableWithProductionKtorTls() = runBlocking(Dispatchers.IO) {
        assumeTrue("Requires real build-time endpoints", !BuildConfig.DATA_LINK.contains("example.invalid"))
        val client = KtorClient().client
        try {
            client.prepareGet(BuildConfig.DATA_LINK) {
                header("User-Agent", "Mozilla/5.0")
                header("Range", "bytes=0-3")
                timeout { requestTimeoutMillis = 30000; connectTimeoutMillis = 15000; socketTimeoutMillis = 15000 }
            }.execute { response ->
                assertTrue("Archive server must return a successful response", response.status.isSuccess())
                val channel: ByteReadChannel = response.body()
                val signature = ByteArray(4)
                var read = 0
                while (read < signature.size) {
                    val count = channel.readAvailable(signature, read, signature.size - read)
                    if (count < 0) break
                    read += count
                }
                assertEquals(4, read)
                assertArrayEquals(byteArrayOf(0x50, 0x4b, 0x03, 0x04), signature)
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun entryMirrorCardPosterAndOneMp4SourceAreReachable() = runBlocking(Dispatchers.IO) {
        val entry = jsoup.loadPage(url = AppConstants.ENTRY_POINT_URL, timeout = 15000)
        val mirror = entry.select("a[href]").firstOrNull { it.text().contains("Фильмы", ignoreCase = true) }
            ?.absUrl("href") ?: error("Entry page contains no movie mirror")
        assertEquals("https", URI(mirror).scheme)
        val catalog = jsoup.loadPage(url = mirror, timeout = 15000)
        val cardUrl = catalog.select("a[href]").firstOrNull {
            URI(it.absUrl("href")).path.orEmpty().matches(Regex("/films/\\d+/?"))
        }?.absUrl("href") ?: error("Catalog contains no film card")
        val card = jsoup.loadPage(url = cardUrl, timeout = 15000)
        assertTrue("Card title must be present", card.select(".blc .acat").text().isNotBlank())
        val poster = card.selectFirst(".screen img")?.absUrl("src") ?: error("Card contains no poster")
        // Same path the cards use: candidate resolution plus the app's own image client.
        val candidates = posterUrlCandidates(poster, mirror)
        assertTrue("Poster path produced no candidates", candidates.isNotEmpty())
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        // asBitmap goes through the same Glide module, browser headers and TLS client as the cards.
        val decoded = try {
            com.bumptech.glide.Glide.with(target).asBitmap().load(poster)
                .submit().get(20, java.util.concurrent.TimeUnit.SECONDS)
        } catch (error: Exception) {
            throw AssertionError("Real poster did not decode through the app image pipeline: $poster", error)
        }
        assertTrue("Real poster decoded to an empty bitmap: $poster", decoded.width > 0 && decoded.height > 0)
        decoded.recycle()
        val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
        client.newCall(Request.Builder().url(poster).header("Referer", mirror).build()).execute().use {
            assertTrue("Poster must be downloadable", it.isSuccessful)
            assertTrue(it.header("Content-Type").orEmpty().startsWith("image/"))
        }
        val link = card.getBestFilmCinemaLink() ?: error("Card contains no MP4 download link")
        val resolved = jsoup.loadUrl(
            url = URI(cardUrl).resolve(link).toString(), requestHeaders = mapOf("Referer" to mirror), timeout = 15000,
        )
        client.newCall(Request.Builder().url(resolved)
            .header("Range", "bytes=0-1023").header("Referer", mirror)
            .header("User-Agent", "Mozilla/5.0").build()).execute().use {
            assertTrue("Resolved movie source must be downloadable", it.isSuccessful)
            assertTrue("Movie source must not be an HTML error page", !it.header("Content-Type").orEmpty().contains("text/html"))
            val bytes = ByteArray(1024)
            assertTrue("Movie source must supply bytes", (it.body?.byteStream()?.read(bytes) ?: -1) > 0)
        }
    }
}
