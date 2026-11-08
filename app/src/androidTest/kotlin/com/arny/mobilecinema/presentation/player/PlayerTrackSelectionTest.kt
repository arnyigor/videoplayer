@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.arny.mobilecinema.presentation.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.test.platform.app.InstrumentationRegistry
import com.arny.mobilecinema.presentation.DeviceGuard
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PlayerTrackSelectionTest {
    private lateinit var player: ExoPlayer

    @Test fun listingTrackMenusKeepsExistingPreferences() {
        DeviceGuard.requireApprovedDevice()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var selector: DefaultTrackSelector
        val ready = CountDownLatch(1)
        try {
            instrumentation.runOnMainSync {
                selector = DefaultTrackSelector(instrumentation.targetContext)
                selector.parameters = selector.parameters.buildUpon()
                    .setPreferredAudioLanguage("de")
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                player = ExoPlayer.Builder(instrumentation.targetContext).setTrackSelector(selector).build().apply {
                    volume = 0f
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY) ready.countDown()
                        }
                    })
                    setMediaSource(SilenceMediaSource.Factory().setDurationUs(3_000_000L).createMediaSource())
                    prepare()
                }
            }
            assertTrue("Audio timeline not ready", ready.await(15, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                val before = selector.parameters
                selector.generateLanguagesList(instrumentation.targetContext)
                selector.generateQualityList(instrumentation.targetContext)
                assertEquals("Reading menus reset track preferences", before, selector.parameters)
                assertTrue(selector.parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
            }
        } finally {
            if (::player.isInitialized) instrumentation.runOnMainSync { player.release() }
        }
    }
}
