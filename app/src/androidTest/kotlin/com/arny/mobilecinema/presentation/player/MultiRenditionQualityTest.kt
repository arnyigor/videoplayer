@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.arny.mobilecinema.presentation.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arny.mobilecinema.presentation.DeviceGuard
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/**
 * K-3: реальный адаптивный поток с тремя рендишнами (фикстура `/multirend/master.m3u8`).
 *
 * Проверяется то, что нельзя проверить юнит-тестом:
 * 1. плеер действительно видит 180p/360p/720p, а не один трек;
 * 2. меню качества выстраивает их от высокого к низкому;
 * 3. выбранное вручную качество применяется;
 * 4. после перехода на следующую серию старый override не «прилипает» к новому треку.
 */
@RunWith(AndroidJUnit4::class)
class MultiRenditionQualityTest {

    private lateinit var player: ExoPlayer
    private lateinit var selector: DefaultTrackSelector

    private val url = "http://127.0.0.1:8765/multirend/master.m3u8"

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        DeviceGuard.requireApprovedDevice()
        onMain {
            selector = DefaultTrackSelector(context)
            player = ExoPlayer.Builder(context).setTrackSelector(selector).build()
        }
    }

    @After
    fun tearDown() {
        onMain { player.release() }
    }

    @Test
    fun adaptiveStreamExposesEveryRendition() {
        onMain { player.setMediaItem(MediaItem.fromUri(url)); player.prepare() }
        assertTrue("playlist must become ready", awaitReady())
        assertEquals(listOf("720p (1280 × 720)", "360p (640 × 360)", "180p (320 × 180)"), qualityMenu().map { it.first })
    }

    @Test
    fun manualQualityIsAppliedAndStaleOverrideDoesNotLeakIntoNextItem() {
        onMain { player.setMediaItem(MediaItem.fromUri(url)); player.prepare() }
        assertTrue("playlist must become ready", awaitReady())

        val hd720 = qualityMenu().first { it.first.startsWith("720p") }.second
        onMain {
            selector.parameters = selector.parameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .addOverride(hd720)
                .build()
        }
        assertTrue("720p must become the selected video track", awaitSelectedHeight(720))

        // Вторая серия: тот же адаптивный поток, но новый media item.
        onMain {
            player.addMediaItem(MediaItem.fromUri(url))
            player.seekToNextMediaItem()
        }
        assertEquals(1, onMain { player.currentMediaItemIndex })
        assertTrue("second item must become ready", awaitReady())

        val heights = onMain {
            player.currentTracks.groups
                .filter { it.type == C.TRACK_TYPE_VIDEO }
                .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it).height } }
                .toSet()
        }
        assertEquals("all renditions must be visible again for the new item", setOf(180, 360, 720), heights)
    }

    private fun qualityMenu(): List<Pair<String, TrackSelectionOverride>> = onMain {
        videoQualityMenu(
            player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { group ->
                (0 until group.length).mapNotNull { index ->
                    if (!group.isTrackSupported(index)) return@mapNotNull null
                    val format = group.getTrackFormat(index)
                    Triple(format.width, format.height,
                        TrackSelectionOverride(group.mediaTrackGroup, listOf(index)))
                }
            }
        )
    }

    private fun awaitReady(timeoutMs: Long = 25_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (onMain { player.playbackState == Player.STATE_READY }) return true
            if (onMain { player.playerError } != null) return false
            Thread.sleep(100)
        }
        return false
    }

    private fun awaitSelectedHeight(height: Int, timeoutMs: Long = 15_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val selected = onMain {
                player.currentTracks.groups
                    .filter { it.type == C.TRACK_TYPE_VIDEO }
                    .any { group ->
                        (0 until group.length).any { group.isTrackSelected(it) && group.getTrackFormat(it).height == height }
                    }
            }
            if (selected) return true
            Thread.sleep(100)
        }
        return false
    }

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<T>()
        val error = AtomicReference<Throwable>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                result.set(block())
            } catch (t: Throwable) {
                error.set(t)
            }
        }
        error.get()?.let { throw it }
        return result.get()
    }
}
