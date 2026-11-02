package com.arny.mobilecinema.presentation.playerview

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arny.mobilecinema.presentation.DeviceGuard
import com.arny.mobilecinema.presentation.player.PlayerSourceMedia3
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * S-011: Media3-адаптер источников на реальных ответах фикстурного сервера.
 *
 * Проверяется переходный слой (`PlayerSourceMedia3` + `androidx.media3.exoplayer.ExoPlayer`),
 * а не экран: экран приложения пока остаётся на ExoPlayer2. MEDIA3 READY / first frame
 * проверяются теми же fake URL, что и в матrice отказов (S-071).
 */
@RunWith(AndroidJUnit4::class)
class Media3AdapterPlaybackTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val adapter = PlayerSourceMedia3(OkHttpClient(), "anwap-instrumentation")
    private var player: ExoPlayer? = null
    private var texture: SurfaceTexture? = null
    private var output: Surface? = null
    private val firstFrame = CountDownLatch(1)
    private val errorRef = AtomicReference<Throwable?>(null)

    @Before
    fun setUp() {
        DeviceGuard.requireApprovedDevice()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            texture = SurfaceTexture(false).also { it.setDefaultBufferSize(160, 90) }
            output = Surface(texture)
            player = ExoPlayer.Builder(context).build().also { exo ->
                exo.setVideoSurface(output)
                exo.addAnalyticsListener(object : AnalyticsListener {
                    override fun onRenderedFirstFrame(
                        eventTime: AnalyticsListener.EventTime,
                        surface: Any,
                        renderTimeMs: Long
                    ) {
                        firstFrame.countDown()
                    }

                    override fun onPlayerError(
                        eventTime: AnalyticsListener.EventTime,
                        error: androidx.media3.common.PlaybackException
                    ) {
                        errorRef.set(error)
                    }
                })
            }
        }
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            player?.release()
            player = null
            output?.release()
            texture?.release()
        }
    }

    private fun play(url: String) {
        val source = adapter.source(url)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            player!!.setMediaSource(source)
            player!!.prepare()
            player!!.playWhenReady = true
        }
    }

    @Test
    fun progressiveFakeUrlRendersFirstFrame() {
        play("http://127.0.0.1:8765/test.mp4")
        assertTrue("Media3 first frame missing: ${errorRef.get()}", firstFrame.await(25, TimeUnit.SECONDS))
    }

    @Test
    fun hlsFakeUrlRendersFirstFrame() {
        play("http://127.0.0.1:8765/test.m3u8")
        assertTrue("Media3 HLS first frame missing: ${errorRef.get()}", firstFrame.await(25, TimeUnit.SECONDS))
    }

    @Test
    fun forbiddenUrlReportsErrorInsteadOfHanging() {
        play("http://127.0.0.1:8765/forbidden")
        val deadline = System.currentTimeMillis() + 20_000
        while (errorRef.get() == null && System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(100)
        }
        assertTrue("Media3 did not report 403", errorRef.get() != null)
    }

    @Test
    fun metadataCarriesEpisodeCoordinates() {
        // Метаданные — контракт прогресса: сезон/серия должны доехать до плеера через адаптер.
        val source = adapter.source(
            "http://127.0.0.1:8765/test.mp4",
            title = "Серия 2",
            season = 1,
            episode = 1,
            mediaId = "ep:2:2:201"
        )
        val item = source.mediaItem
        assertTrue(item.mediaId == "ep:2:2:201")
        assertTrue(item.mediaMetadata.title == "Серия 2")
        // Ключи — константы приложения, а не строки на глаз: их использует сохранение прогресса.
        assertEquals(
            1,
            item.mediaMetadata.extras?.getInt(com.arny.mobilecinema.data.repository.AppConstants.Player.SEASON)
        )
        assertEquals(
            1,
            item.mediaMetadata.extras?.getInt(com.arny.mobilecinema.data.repository.AppConstants.Player.EPISODE)
        )
    }
}
