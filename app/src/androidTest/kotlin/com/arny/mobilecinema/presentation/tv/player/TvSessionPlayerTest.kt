@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.arny.mobilecinema.presentation.tv.player

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.test.platform.app.InstrumentationRegistry
import com.arny.mobilecinema.presentation.DeviceGuard
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Go through an actual session controller, not Window key dispatch. */
class TvSessionPlayerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private lateinit var controller: MediaController
    private val ready = CountDownLatch(1)
    private val command = CountDownLatch(1)
    private val pauses = AtomicInteger()
    private val pendingPlays = AtomicInteger()

    @Before fun connect() {
        DeviceGuard.requireApprovedDevice()
        lateinit var future: com.google.common.util.concurrent.ListenableFuture<MediaController>
        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(instrumentation.targetContext).build().apply {
                volume = 0f
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == androidx.media3.common.Player.STATE_READY) ready.countDown()
                    }
                })
                setMediaSource(SilenceMediaSource.Factory().setDurationUs(3_000_000L).createMediaSource())
                prepare()
            }
            val wrapper = TvSessionPlayer(player,
                onPause = { pauses.incrementAndGet(); command.countDown() },
                onPlayPending = { pendingPlays.incrementAndGet(); command.countDown(); true })
            session = MediaSession.Builder(instrumentation.targetContext, wrapper).build()
            future = MediaController.Builder(instrumentation.targetContext, session.token).buildAsync()
        }
        controller = future.get(15, TimeUnit.SECONDS)
        assertTrue("Session timeline not ready", ready.await(15, TimeUnit.SECONDS))
    }

    @After fun release() {
        instrumentation.runOnMainSync {
            if (::controller.isInitialized) controller.release()
            if (::session.isInitialized) session.release()
            if (::player.isInitialized) player.release()
        }
    }

    @Test fun controllerPauseCancelsPendingCountdownEvenWhenAlreadyPaused() {
        instrumentation.runOnMainSync { assertFalse(player.playWhenReady); controller.pause() }
        assertTrue("Session Pause did not reach countdown cancellation", command.await(10, TimeUnit.SECONDS))
        assertTrue(pauses.get() > 0)
        instrumentation.runOnMainSync { assertFalse(player.playWhenReady) }
    }

    @Test fun controllerPlayUsesPendingEpisodeInsteadOfRestartingFinishedItem() {
        instrumentation.runOnMainSync { controller.play() }
        assertTrue("Session Play bypassed pending episode", command.await(10, TimeUnit.SECONDS))
        assertTrue(pendingPlays.get() > 0)
        instrumentation.runOnMainSync { assertFalse(player.playWhenReady) }
    }

    @Test fun controllerStopCancelsPendingCountdown() {
        instrumentation.runOnMainSync { controller.stop() }
        assertTrue("Session Stop did not cancel countdown", command.await(10, TimeUnit.SECONDS))
        assertTrue(pauses.get() > 0)
    }
}
