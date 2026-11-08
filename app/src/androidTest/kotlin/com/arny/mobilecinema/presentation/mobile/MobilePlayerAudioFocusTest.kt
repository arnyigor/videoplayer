package com.arny.mobilecinema.presentation.mobile

import android.app.PictureInPictureParams
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.common.Player
import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.models.AnwapUrl
import com.arny.mobilecinema.domain.models.CinemaUrlData
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieInfo
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.presentation.DeviceGuard
import com.arny.mobilecinema.presentation.MainActivity
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Losing audio focus while in PiP used to be swallowed by the picture-in-picture early return,
 * so a call or another player could keep the sound running.
 */
@RunWith(AndroidJUnit4::class)
class MobilePlayerAudioFocusTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var focusRequest: AudioFocusRequest? = null

    private val movie = Movie(
        dbId = 9006,
        movieId = 9006,
        title = "Fixture focus",
        type = MovieType.CINEMA,
        info = MovieInfo(),
        cinemaUrlData = CinemaUrlData(hdUrl = AnwapUrl(urls = listOf("http://127.0.0.1:8765/multitrack.mkv"))),
    )

    @Before fun launch() {
        DeviceGuard.requireApprovedDevice()
        assumeTrue("PiP needs API 26+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        shell("appops set ${instrumentation.targetContext.packageName} PICTURE_IN_PICTURE allow")
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun release() {
        focusRequest?.let {
            (instrumentation.targetContext.getSystemService(AudioManager::class.java))
                .abandonAudioFocusRequest(it)
        }
        scenario?.close()
        scenario = null
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
    }

    private fun <T> viaActivity(block: (MainActivity) -> T): T {
        var result: T? = null
        scenario!!.onActivity { result = block(it) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    // Every ExoPlayer read has to happen on the main thread; the helper above returns the instance.
    private fun playerReady(): Boolean = viaActivity { activity ->
        val view = playerView(activity)
        view?.player?.playbackState == Player.STATE_READY
    }

    private fun playWhenReady(): Boolean? = viaActivity { activity -> playerView(activity)?.player?.playWhenReady }

    private fun isPlaying(): Boolean? = viaActivity { activity -> playerView(activity)?.player?.isPlaying }

    private fun play() = viaActivity { activity -> playerView(activity)?.player?.play() }

    private fun playerView(activity: MainActivity): androidx.media3.ui.PlayerView? {
        fun find(view: View): View? {
            if (view.javaClass.name == "com.github.vkay94.dtpv.DoubleTapPlayerView") return view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
            }
            return null
        }
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        return find(root) as? androidx.media3.ui.PlayerView
    }

    private fun openPlayer() {
        val controller = viaActivity {
            (it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
        }
        val args = Bundle().apply {
            putString("path", "http://127.0.0.1:8765/multitrack.mkv")
            putParcelable("movie", movie)
            putString("playId", "focus-check")
        }
        instrumentation.runOnMainSync { controller.navigate(R.id.nav_player_view, args) }
        val deadline = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < deadline) {
            if (playerReady()) return
            Thread.sleep(500)
        }
    }

    @Test fun audioFocusLossPausesPlaybackInPictureInPicture() {
        openPlayer()
        assumeTrue("Player did not become ready; fixture server must be reachable", playerReady())
        play()
        val playingDeadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < playingDeadline) {
            if (playWhenReady() == true && isPlaying() == true) break
            Thread.sleep(300)
        }
        assertTrue("Fixture did not start playing", isPlaying() == true)

        viaActivity { activity ->
            activity.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        }
        val pipDeadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < pipDeadline) {
            if (viaActivity { it.isInPictureInPictureMode }) break
            Thread.sleep(300)
        }
        assertTrue("Could not enter picture-in-picture", viaActivity { it.isInPictureInPictureMode })

        // Another app taking audio focus (call, music player) must pause this one, PiP or not.
        val audioManager = instrumentation.targetContext.getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .build()
        focusRequest = request
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            audioManager.requestAudioFocus(request)
        }

        val pauseDeadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < pauseDeadline) {
            if (playWhenReady() == false) break
            Thread.sleep(200)
        }
        assertFalse("Audio focus loss did not pause playback while in PiP", playWhenReady() ?: true)
    }
}
