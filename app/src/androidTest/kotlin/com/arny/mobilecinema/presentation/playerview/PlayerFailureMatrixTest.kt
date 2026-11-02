package com.arny.mobilecinema.presentation.playerview

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arny.mobilecinema.presentation.DeviceGuard
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.analytics.AnalyticsListener
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
 * S-010: матрица отказов плеера на реальных ответах фикстурного сервера (S-071).
 *
 * Проверяется слой плеера, а не экран: URL подаются напрямую в ExoPlayer, поэтому тест не
 * зависит от вёрстки и наполнения каталога. Сервер фикстур должен быть запущен на стенде
 * (`python scripts/perf_fixtures.py serve`) и проброшен (`adb reverse tcp:8765 tcp:8765`).
 */
@RunWith(AndroidJUnit4::class)
class PlayerFailureMatrixTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var player: ExoPlayer? = null
    private var texture: SurfaceTexture? = null
    private var output: Surface? = null
    private val playerError = AtomicReference<PlaybackException?>(null)
    private val errorReceived = CountDownLatch(1)
    private val firstFrame = CountDownLatch(1)
    private val stateReached = AtomicReference<Int>(Player.STATE_IDLE)

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            playerError.set(error)
            errorReceived.countDown()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            stateReached.set(playbackState)
        }
    }

    private val analytics = object : AnalyticsListener {
        override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, surface: Any, renderTimeMs: Long) {
            firstFrame.countDown()
        }
    }

    @Before
    fun setUp() {
        DeviceGuard.requireApprovedDevice()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            texture = SurfaceTexture(false).also { it.setDefaultBufferSize(160, 90) }
            output = Surface(texture)
            player = ExoPlayer.Builder(context).build().also {
                it.setVideoSurface(output)
                it.addListener(listener)
                it.addAnalyticsListener(analytics)
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
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            player!!.setMediaItem(MediaItem.fromUri(url))
            player!!.prepare()
            player!!.playWhenReady = true
        }
    }

    private fun awaitError(timeoutMs: Long = 10_000): PlaybackException? {
        errorReceived.await(timeoutMs, TimeUnit.MILLISECONDS)
        return playerError.get()
    }

    @Test
    fun forbiddenResponseIsReportedAsHttpError() {
        play("http://127.0.0.1:8765/forbidden")
        val error = awaitError()
        assertTrue("ожидалась ошибка HTTP, получено: $error", error != null)
        assertEquals(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            error!!.errorCode
        )
    }

    @Test
    fun missingMediaIsReportedAsHttpError() {
        play("http://127.0.0.1:8765/no-such-media.mp4")
        val error = awaitError()
        assertTrue("ожидалась ошибка HTTP, получено: $error", error != null)
        assertEquals(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, error!!.errorCode)
    }

    @Test
    fun closedPortIsReportedAsIoError() {
        // Порт закрыт → нет соединения; это же поведение при недоступном сервере.
        play("http://127.0.0.1:8799/test.mp4")
        val error = awaitError()
        assertTrue("ожидалась сетевая ошибка, получено: $error", error != null)
        assertTrue(
            "код ${error!!.errorCode} не относится к вводу-выводу",
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        )
    }

    @Test
    fun offlineNetworkReportsErrorInsteadOfWaitingForever() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) {
            automation.executeShellCommand(command).use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            }
        }
        val wifi = android.provider.Settings.Global.getInt(context.contentResolver, "wifi_on", 0)
        val data = android.provider.Settings.Global.getInt(context.contentResolver, "mobile_data", 0)
        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
        try {
            shell("svc wifi disable")
            shell("svc data disable")
            val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
            while (connectivity.activeNetwork != null && android.os.SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(100)
            }
            assertEquals("Device must actually be offline", null, connectivity.activeNetwork)
            // HTTPS reserved address; unlike adb-reversed loopback it cannot bypass offline state.
            play("https://192.0.2.1/test.mp4")
            val error = awaitError(20_000)
            assertTrue("Offline load did not report an error", error != null)
            assertTrue("Unexpected offline error: ${error!!.errorCode}",
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
        } finally {
            shell("svc wifi ${if (wifi != 0) "enable" else "disable"}")
            shell("svc data ${if (data != 0) "enable" else "disable"}")
        }
    }

    @Test
    fun unresolvedHostReportsNetworkError() {
        // RFC 2606 reserved suffix: no real media URI or private hostname involved.
        play("https://playback-test.invalid/test.mp4")
        val error = awaitError(20_000)
        assertTrue("DNS failure did not reach player listener", error != null)
        assertEquals(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, error!!.errorCode)
        assertTrue(
            "Expected UnknownHostException in cause chain",
            generateSequence<Throwable>(error) { it.cause }
                .any { it is java.net.UnknownHostException }
        )
    }

    @Test
    fun redirectIsFollowedAndFirstFrameRendered() {
        play("http://127.0.0.1:8765/redirect")
        assertTrue(
            "первый кадр не пришёл после 302-перехода",
            firstFrame.await(20, TimeUnit.SECONDS)
        )
    }

    @Test
    fun hlsManifestRendersFirstFrame() {
        play("http://127.0.0.1:8765/test.m3u8")
        assertTrue("HLS first frame missing", firstFrame.await(20, TimeUnit.SECONDS))
        assertEquals(null, playerError.get())
    }

    @Test
    fun slowResponseReachesReady() {
        play("http://127.0.0.1:8765/slow?ms=1500")
        // Тело не медиа: главное — плеер не виснет и не молчит, а приходит к ошибке или READY.
        val error = awaitError(15_000)
        val state = stateReached.get()
        assertTrue(
            "плеер остался в состоянии $state без ошибки и без READY",
            error != null || state == Player.STATE_READY || state == Player.STATE_ENDED
        )
    }
}
