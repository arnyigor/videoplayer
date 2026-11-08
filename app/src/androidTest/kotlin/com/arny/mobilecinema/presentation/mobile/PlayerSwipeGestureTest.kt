package com.arny.mobilecinema.presentation.mobile

import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.Player
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.models.AnwapUrl
import com.arny.mobilecinema.domain.models.CinemaUrlData
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieInfo
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.presentation.DeviceGuard
import com.arny.mobilecinema.presentation.MainActivity
import com.github.vkay94.dtpv.DoubleTapPlayerView
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Vertical swipes on the player: right half changes volume, left half changes brightness.
 * A plain tap must not change either, so the double-tap seek stays untouched.
 */
@RunWith(AndroidJUnit4::class)
class PlayerSwipeGestureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var audioManager: AudioManager

    private val movie = Movie(
        dbId = 9007,
        movieId = 9007,
        title = "Fixture swipe",
        type = MovieType.CINEMA,
        info = MovieInfo(),
        cinemaUrlData = CinemaUrlData(hdUrl = AnwapUrl(urls = listOf("http://127.0.0.1:8765/multitrack.mkv"))),
    )

    @Before fun launch() {
        DeviceGuard.requireApprovedDevice()
        assertTrue("Swipe test needs an isolated APK", instrumentation.targetContext.packageName.endsWith(".perfdebug") ||
            instrumentation.targetContext.packageName.endsWith(".tvdebug"))
        audioManager = instrumentation.targetContext.getSystemService(AudioManager::class.java)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun close() = scenario?.close() ?: Unit

    private fun <T> onActivity(block: (MainActivity) -> T): T {
        var result: T? = null
        scenario!!.onActivity { result = block(it) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun playerView(activity: MainActivity): View? {
        fun find(view: View): View? {
            if (view is DoubleTapPlayerView) return view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
            }
            return null
        }
        return find(activity.findViewById(android.R.id.content))
    }

    private fun openPlayer() {
        val controller = onActivity {
            (it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
        }
        val args = Bundle().apply {
            putString("path", "http://127.0.0.1:8765/multitrack.mkv")
            putParcelable("movie", movie)
            putString("playId", "swipe-check")
        }
        instrumentation.runOnMainSync { controller.navigate(R.id.nav_player_view, args) }
        val deadline = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < deadline) {
            val ready = onActivity { activity ->
                playerView(activity)?.let { (it as DoubleTapPlayerView).player?.playbackState == Player.STATE_READY } == true
            }
            if (ready) return
            Thread.sleep(300)
        }
    }

    private fun swipe(xFraction: Float, dyFraction: Float) {
        val view = onActivity { playerView(it) } ?: return
        val startTime = SystemClock.uptimeMillis()
        val x = view.width * xFraction
        val startY = view.height * 0.8f
        val endY = view.height * (0.8f - dyFraction)
        instrumentation.runOnMainSync {
            var time = startTime
            view.dispatchTouchEvent(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, startY, 0))
            val steps = 8
            for (step in 1..steps) {
                time = startTime + step * 16L
                val y = startY + (endY - startY) * step / steps
                view.dispatchTouchEvent(MotionEvent.obtain(time, time, MotionEvent.ACTION_MOVE, x, y, 0))
            }
            view.dispatchTouchEvent(MotionEvent.obtain(time + 16, time + 16, MotionEvent.ACTION_UP, x, endY, 0))
        }
        Thread.sleep(400)
    }

    @Test fun indicatorsUpdateDuringMoveBeforeFingerIsReleased() {
        openPlayer()
        val view = onActivity { playerView(it) }!!
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        instrumentation.runOnMainSync { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max / 4, 0) }
        onActivity { it.window.attributes = it.window.attributes.apply { screenBrightness = 0.2f } }
        // Let any indicator from setup start fading before beginning the next gesture.
        Thread.sleep(1100)
        for (isVolume in listOf(true, false)) {
            val downTime = SystemClock.uptimeMillis()
            val x = view.width * if (isVolume) 0.85f else 0.15f
            val startY = view.height * 0.8f
            fun send(action: Int, y: Float) = instrumentation.runOnMainSync {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
                try { view.dispatchTouchEvent(event) } finally { event.recycle() }
            }
            send(MotionEvent.ACTION_DOWN, startY)
            try {
                for (fraction in listOf(0.1f, 0.2f, 0.3f)) {
                    val y = startY - view.height * fraction
                    // Dispatch and check in the SAME main-thread callback: no observer delivery or UP.
                    onActivity { activity ->
                        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, x, y, 0)
                        try { view.dispatchTouchEvent(event) } finally { event.recycle() }
                        val indicator = activity.findViewById<android.widget.TextView>(
                            if (isVolume) R.id.tvVolume else R.id.tvBrightness)
                        assertEquals(View.VISIBLE, indicator.visibility)
                        assertEquals("Indicator must not restart its fade on MOVE", 1f, indicator.alpha, 0.001f)
                        if (isVolume) {
                            val expected = (max / 4 + fraction / 0.7f * max).toInt()
                            val actual = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            assertTrue("Volume must follow MOVE", kotlin.math.abs(actual - expected) <= 1)
                            assertEquals(actual.toString(), indicator.text.toString())
                        } else {
                            val expected = 0.2f + fraction / 0.7f
                            assertEquals(expected, activity.window.attributes.screenBrightness, 0.01f)
                            assertEquals("${kotlin.math.round(expected * 100).toInt()}%", indicator.text.toString())
                        }
                    }
                    Thread.sleep(400)
                }
            } finally {
                send(MotionEvent.ACTION_UP, startY - view.height * 0.3f)
            }
        }
    }

    @Test fun rightSwipeAboveMaximumBoostsAndDownRemovesBoostFirst() {
        openPlayer()
        fun gain(): Int = onActivity { activity ->
            val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            val fragment = host.childFragmentManager.primaryNavigationFragment!!
            val field = fragment.javaClass.getDeclaredField("enhancer").apply { isAccessible = true }
            val enhancer = field.get(fragment) as android.media.audiofx.LoudnessEnhancer
            enhancer.targetGain.toInt()
        }
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        instrumentation.runOnMainSync { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0) }
        Thread.sleep(300)
        swipe(0.85f, 0.35f)
        val boosted = gain()
        assertTrue("Swipe above maximum must apply actual audio gain", boosted > 0)
        assertEquals(max, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertTrue(onActivity { it.findViewById<android.widget.TextView>(R.id.tvVolume).text.contains("dB") })
        swipe(0.85f, -0.1f)
        assertTrue("Down must reduce boost", gain() < boosted)
        assertTrue("Small downward swipe must retain some boost", gain() > 0)
        assertEquals("Reduce boost before system volume", max, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        swipe(0.85f, -0.5f)
        assertEquals(0, gain())
        assertTrue(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) < max)
    }

    @Test fun rightSwipeUpRaisesVolumeAndDownLowersIt() {
        openPlayer()
        assumeTrue("Player did not become ready", onActivity { playerView(it) } != null)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        instrumentation.runOnMainSync { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max / 3, 0) }
        val before = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        swipe(xFraction = 0.85f, dyFraction = 0.35f)
        val afterUp = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        assertTrue("Upward swipe on the right must raise volume ($before → $afterUp)", afterUp > before)

        swipe(xFraction = 0.85f, dyFraction = -0.35f)
        val afterDown = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        assertTrue("Downward swipe on the right must lower volume ($afterUp → $afterDown)", afterDown < afterUp)
    }

    @Test fun leftSwipeChangesScreenBrightness() {
        openPlayer()
        assumeTrue("Player did not become ready", onActivity { playerView(it) } != null)
        onActivity { activity -> activity.window.attributes = activity.window.attributes.apply { screenBrightness = 0.3f } }

        swipe(xFraction = 0.15f, dyFraction = 0.35f)
        val brighter = onActivity { it.window.attributes.screenBrightness }
        assertTrue("Upward swipe on the left must raise brightness ($brighter)", brighter > 0.35f)

        swipe(xFraction = 0.15f, dyFraction = -0.35f)
        val darker = onActivity { it.window.attributes.screenBrightness }
        assertTrue("Downward swipe on the left must lower brightness ($darker)", darker < brighter)
    }

    @Test fun plainTapDoesNotChangeVolumeOrBrightness() {
        openPlayer()
        assumeTrue("Player did not become ready", onActivity { playerView(it) } != null)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        instrumentation.runOnMainSync { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max / 2, 0) }
        onActivity { activity -> activity.window.attributes = activity.window.attributes.apply { screenBrightness = 0.4f } }
        val volumeBefore = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val brightnessBefore = onActivity { it.window.attributes.screenBrightness }

        val view = onActivity { playerView(it) }!!
        instrumentation.runOnMainSync {
            val time = SystemClock.uptimeMillis()
            val x = view.width * 0.5f
            val y = view.height * 0.5f
            view.dispatchTouchEvent(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0))
            view.dispatchTouchEvent(MotionEvent.obtain(time + 40, time + 40, MotionEvent.ACTION_UP, x, y, 0))
        }
        Thread.sleep(500)
        assertEquals("A tap must not change volume", volumeBefore, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertEquals("A tap must not change brightness", brightnessBefore,
            onActivity { it.window.attributes.screenBrightness }, 0.001f)
        assertTrue("Double tap must stay enabled", onActivity { (playerView(it) as DoubleTapPlayerView).isDoubleTapEnabled })
    }
}
