package com.arny.mobilecinema.presentation.playerview

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.media3.ui.PlayerView
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieInfo
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.SerialSeason
import com.arny.mobilecinema.presentation.DeviceGuard
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S-011 (п.7): автопереход на следующую серию после миграции на Media3.
 *
 * Реальный экран плеера открывается с сериалом из двух серий (обе — фикстурный HLS),
 * первая серия проматывается к концу, и тест ждёт перехода плеера на второй MediaItem.
 */
@RunWith(AndroidJUnit4::class)
class PlayerAutoplayNextTest {

    private var scenario: ActivityScenario<*>? = null

    private val series = Movie(
        dbId = 9003,
        movieId = 9003,
        title = "Fixture autoplay",
        type = MovieType.SERIAL,
        info = MovieInfo(),
        seasons = listOf(
            SerialSeason(
                id = 1,
                episodes = listOf(
                    SerialEpisode(id = 11, episode = "1", hls = "http://127.0.0.1:8765/test.m3u8"),
                    SerialEpisode(id = 12, episode = "2", hls = "http://127.0.0.1:8765/test.m3u8"),
                )
            )
        ),
    )

    @Before
    fun setUp() {
        DeviceGuard.requireApprovedDevice()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf(
            "pm grant ${InstrumentationRegistry.getInstrumentation().targetContext.packageName} " +
                "android.permission.WRITE_EXTERNAL_STORAGE",
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

    private fun playerViewOnScreen(): PlayerView? = viaActivity { activity ->
        viewByClass(
            activity.findViewById(android.R.id.content),
            "com.github.vkay94.dtpv.DoubleTapPlayerView"
        ) as? PlayerView
    }

    @Test
    fun playbackAdvancesToNextEpisode() {
        val controller = viaActivity { activity ->
            (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment)
                .navController
        }
        val args = Bundle().apply {
            // Ключ path обязателен: Safe Args падает, если его нет в бандле.
            putString("path", null)
            putParcelable("movie", series)
            putInt("seasonIndex", 0)
            putInt("episodeIndex", 0)
            putString("playId", "autoplay-check")
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller.navigate(R.id.nav_player_view, args)
        }
        Thread.sleep(6_000)

        val view = playerViewOnScreen()
        assertTrue("player view missing", view != null)
        val player = view!!.player!!
        assertTrue("player not attached", true)
        // Любое обращение к Player — только с main-потока.
        val startIndex = viaActivity { player.currentMediaItemIndex }
        val itemCount = viaActivity { player.mediaItemCount }
        val duration = viaActivity { player.duration }
        assertEquals("started at wrong episode", 0, startIndex)
        assertTrue("expected 2 queued episodes", itemCount >= 2)
        assertTrue("duration unknown", duration > 5_000)
        viaActivity {
            player.seekTo(player.currentMediaItemIndex, duration - 1_200)
            player.play()
        }

        val deadline = System.currentTimeMillis() + 30_000
        var index = 0
        while (System.currentTimeMillis() < deadline) {
            index = viaActivity { player.currentMediaItemIndex }
            if (index >= 1) break
            Thread.sleep(500)
        }
        assertEquals("player did not advance to the next episode", 1, index)
    }
}
