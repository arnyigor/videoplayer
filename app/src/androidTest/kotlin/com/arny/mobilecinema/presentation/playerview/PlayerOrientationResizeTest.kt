package com.arny.mobilecinema.presentation.playerview

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.media3.ui.AspectRatioFrameLayout
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S-011 (п.4/п.7): смена ориентации и режима вписывания после миграции на Media3.
 *
 * Проверяется, что вьюха остаётся Media3-плеером, `resizeMode` соответствует настройке
 * приложения, а воспроизведение не останавливается при повороте.
 */
@RunWith(AndroidJUnit4::class)
class PlayerOrientationResizeTest {

    private var scenario: ActivityScenario<*>? = null

    private val movie = Movie(
        dbId = 9004,
        movieId = 9004,
        title = "Fixture orientation",
        type = MovieType.CINEMA,
        info = MovieInfo(),
        cinemaUrlData = CinemaUrlData(hdUrl = AnwapUrl(urls = listOf("http://127.0.0.1:8765/test.mp4"))),
    )

    @Before
    fun setUp() {
        DeviceGuard.requireApprovedDevice()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(
                "pm grant ${InstrumentationRegistry.getInstrumentation().targetContext.packageName} " +
                    "android.permission.WRITE_EXTERNAL_STORAGE"
            )
            .use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
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

    private fun playerView(): PlayerView? = viaActivity { activity ->
        viewByClass(
            activity.findViewById(android.R.id.content),
            "com.github.vkay94.dtpv.DoubleTapPlayerView"
        ) as? PlayerView
    }

    @Test
    fun rotationKeepsMedia3PlayerAndResizeMode() {
        val controller = viaActivity { activity ->
            (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment)
                .navController
        }
        val args = Bundle().apply {
            putString("path", "http://127.0.0.1:8765/test.mp4")
            putParcelable("movie", movie)
            putString("playId", "orientation-check")
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller.navigate(R.id.nav_player_view, args)
        }
        Thread.sleep(6_000)

        val portraitView = playerView()
        assertTrue("player view missing in portrait", portraitView != null)
        assertEquals(
            "в портрете режим вписывания должен быть FIT",
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            portraitView!!.resizeMode
        )
        val player = portraitView.player
        assertTrue("player not attached", player != null)

        viaActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        Thread.sleep(3_000)
        val orientation = viaActivity { it.resources.configuration.orientation }
        assertEquals("activity did not rotate", Configuration.ORIENTATION_LANDSCAPE, orientation)

        val landscapeView = playerView()
        assertTrue("player view missing after rotation", landscapeView != null)
        assertEquals("вьюха после поворота должна остаться Media3 PlayerView-совместимой",
            "com.github.vkay94.dtpv.DoubleTapPlayerView", landscapeView!!.javaClass.name)

        // Воспроизведение не должно вставать при повороте.
        val before = viaActivity { player!!.currentPosition }
        Thread.sleep(2_500)
        val after = viaActivity { player!!.currentPosition }
        assertTrue("playback stalled after rotation: before=$before after=$after", after > before + 800)

        viaActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        Thread.sleep(2_000)
        assertEquals(
            Configuration.ORIENTATION_PORTRAIT,
            viaActivity { it.resources.configuration.orientation }
        )
    }
}
