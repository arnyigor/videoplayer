@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.arny.mobilecinema.presentation.tv.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.test.platform.app.InstrumentationRegistry
import com.arny.mobilecinema.presentation.DeviceGuard
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real Media3 timelines on the TV device; no UI assumptions, network or application data. */
class TvPlaybackMediaTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var player: ExoPlayer
    private val failure = AtomicReference<PlaybackException?>()

    @Before
    fun createPlayer() {
        DeviceGuard.requireApprovedDevice()
        org.junit.Assume.assumeTrue("Requires Android TV", instrumentation.targetContext.packageManager
            .hasSystemFeature("android.software.leanback"))
        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(instrumentation.targetContext).build().apply {
                configureTvPlayback()
                assertEquals(SeekParameters.EXACT, seekParameters)
                volume = 0f
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) { failure.set(error) }
                })
            }
        }
    }

    @After
    fun releasePlayer() {
        if (::player.isInitialized) instrumentation.runOnMainSync { player.release() }
    }

    @Test
    fun filmSourceStartsAtSavedPosition() {
        val ready = CountDownLatch(1)
        instrumentation.runOnMainSync {
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) { player.pause(); ready.countDown() }
                }
            })
            val source = SilenceMediaSource.Factory().setDurationUs(10_000_000L).createMediaSource()
            player.prepareTvSource(source, positionMs = 4500L)
        }
        assertTrue("Player did not become ready: ${failure.get()}", ready.await(15, TimeUnit.SECONDS))
        instrumentation.runOnMainSync {
            // Audio may advance a few milliseconds before the READY callback's pause is applied.
            assertTrue("Unexpected resume position: ${player.currentPosition}", player.currentPosition in 4500L..4750L)
            assertEquals(0, player.currentMediaItemIndex)
        }
        assertNull(failure.get())
    }

    @Test
    fun episodeBoundaryPausesBeforeNextItemAndManualContinueWorks() {
        val boundary = CountDownLatch(1)
        val ended = CountDownLatch(1)
        instrumentation.runOnMainSync {
            player.addListener(object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                        player.currentMediaItemIndex == 0) boundary.countDown()
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED && player.currentMediaItemIndex == 1) ended.countDown()
                }
            })
            player.setMediaSources(listOf(
                SilenceMediaSource.Factory().setDurationUs(400_000L).createMediaSource(),
                SilenceMediaSource.Factory().setDurationUs(400_000L).createMediaSource(),
            ))
            player.prepare()
            player.play()
        }
        assertTrue("No intermediate item boundary: ${failure.get()}", boundary.await(15, TimeUnit.SECONDS))
        instrumentation.runOnMainSync {
            assertFalse(player.playWhenReady)
            assertEquals(0, player.currentMediaItemIndex)
            assertTrue(player.hasNextMediaItem())
            player.seekToNextMediaItem()
            player.play()
        }
        assertTrue("Next item did not complete: ${failure.get()}", ended.await(15, TimeUnit.SECONDS))
        assertNull(failure.get())
    }
}
