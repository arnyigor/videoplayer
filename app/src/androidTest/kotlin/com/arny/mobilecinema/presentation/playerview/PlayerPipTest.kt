package com.arny.mobilecinema.presentation.playerview

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import androidx.media3.ui.PlayerView
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
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S-011: PiP после миграции на Media3.
 *
 * PiP проверяется на реальном экране плеера: если мигрированный engine/вьюха не отдают
 * видеоповерхность в режим «картинка в картинке», воспроизведение в PiP остановится.
 */
@RunWith(AndroidJUnit4::class)
class PlayerPipTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<*>? = null

    private val movie = Movie(
        dbId = 9002,
        movieId = 9002,
        title = "Fixture PiP",
        type = MovieType.CINEMA,
        info = MovieInfo(),
        cinemaUrlData = CinemaUrlData(hdUrl = AnwapUrl(urls = listOf("http://127.0.0.1:8765/test.mp4"))),
    )

    @Before
    fun setUp() {
        DeviceGuard.requireApprovedDevice()
        assumeTrue("API 26+ required for PiP", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf(
            "pm grant ${context.packageName} android.permission.WRITE_EXTERNAL_STORAGE",
            "pm grant ${context.packageName} android.permission.READ_EXTERNAL_STORAGE",
        ).forEach { command ->
            automation.executeShellCommand(command).use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            }
        }
        scenario = ActivityScenario.launch(com.arny.mobilecinema.presentation.MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    private fun <T> viaActivity(block: (androidx.appcompat.app.AppCompatActivity) -> T): T {
        var result: T? = null
        scenario!!.onActivity { activity ->
            result = block(activity as androidx.appcompat.app.AppCompatActivity)
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun viewByClass(root: View, className: String): View? {
        if (root.javaClass.name == className) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                viewByClass(root.getChildAt(index), className)?.let { return it }
            }
        }
        return null
    }

    private fun openPlayer() {
        val controller = viaActivity { activity ->
            (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment)
                .navController
        }
        val args = Bundle().apply {
            putString("path", "http://127.0.0.1:8765/test.mp4")
            putParcelable("movie", movie)
            putString("playId", "pip-check")
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller.navigate(R.id.nav_player_view, args)
        }
    }

    @Test
    fun pictureInPictureKeepsPlaybackRunning() {
        openPlayer()
        Thread.sleep(6_000)

        val playerView = viaActivity { activity ->
            viewByClass(
                activity.findViewById(android.R.id.content),
                "com.github.vkay94.dtpv.DoubleTapPlayerView"
            ) as? PlayerView
        }
        assertTrue("player view not found on screen", playerView != null)
        val player = playerView!!.player
        assertTrue("player not attached", player != null)

        val entered = viaActivity { activity ->
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            activity.enterPictureInPictureMode(params)
        }
        Thread.sleep(2_000)
        assertTrue("activity did not enter PiP", entered)
        val inPip = viaActivity { it.isInPictureInPictureMode }
        assertTrue("isInPictureInPictureMode == false after enter", inPip)

        // В PiP воспроизведение обязано продолжаться: это и есть проверка, что поверхность
        // мигрированного плеера живёт в отдельном окне.
        val before = viaActivity { player!!.currentPosition }
        Thread.sleep(2_500)
        val after = viaActivity { player!!.currentPosition }
        assertTrue("playback stalled in PiP: before=$before after=$after", after > before + 1_000)
    }
}
