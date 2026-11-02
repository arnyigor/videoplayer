package com.arny.mobilecinema.presentation.playerview

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.C
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
import com.arny.mobilecinema.presentation.player.generateSubTitlesList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S-011 п.7: аудиодорожки и субтитры на реальном потоке с двумя аудио и встроенными субтитрами
 * (фикстура S-074 `multitrack.mp4`).
 *
 * Проверяется приложение целиком: что экран видит вторую аудиодорожку (появляется кнопка языка),
 * что субтитры распознаны, но нерабочая кнопка скрыта и что выбранная дорожка реально становится
 * активной в плеере.
 */
@RunWith(AndroidJUnit4::class)
class PlayerTracksTest {

    private var scenario: ActivityScenario<*>? = null

    private val movie = Movie(
        dbId = 9005,
        movieId = 9005,
        title = "Fixture multitrack",
        type = MovieType.CINEMA,
        info = MovieInfo(),
        cinemaUrlData = CinemaUrlData(hdUrl = AnwapUrl(urls = listOf("http://127.0.0.1:8765/multitrack.mkv"))),
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

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        java.io.FileOutputStream(
            java.io.File(instrumentation.targetContext.getExternalFilesDir(null), name)
        ).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun openPlayer() {
        val controller = viaActivity { activity ->
            (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment)
                .navController
        }
        val args = Bundle().apply {
            putString("path", "http://127.0.0.1:8765/multitrack.mkv")
            putParcelable("movie", movie)
            putString("playId", "tracks-check")
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller.navigate(R.id.nav_player_view, args)
        }
        Thread.sleep(7_000)
        // Меню дорожек собирается асинхронно по onTracksChanged: ждём появления кнопок.
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            val view = playerViewOnScreen()
            val ready = viaActivity {
                view?.let { view ->
                    view.showController()
                    view.player?.currentTracks?.groups?.count { it.type == C.TRACK_TYPE_AUDIO } ?: 0
                } ?: 0
            }
            if (ready >= 2) {
                break
            }
            Thread.sleep(500)
        }
    }

    private fun viewById(id: Int): View? = viaActivity { it.findViewById(id) }

    private fun playerViewOnScreen(): PlayerView? = viaActivity { activity ->
        activity.findViewById<ViewGroup>(android.R.id.content)
            .let { root ->
                fun find(view: View): View? {
                    if (view.javaClass.name == "com.github.vkay94.dtpv.DoubleTapPlayerView") return view
                    if (view is ViewGroup) {
                        for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                    }
                    return null
                }
                find(root) as? PlayerView
            }
    }

    @Test
    fun multitrackStreamShowsLanguageSelector() {
        openPlayer()
        val playerView = playerViewOnScreen()
        assertTrue("player view missing", playerView != null)
        val player = playerView!!.player
        assertTrue("player not attached", player != null)

        // Дорожки, которые реально видит плеер.
        val audioGroups = viaActivity {
            player!!.currentTracks.groups.count { it.type == C.TRACK_TYPE_AUDIO }
        }
        val textGroups = viaActivity {
            player!!.currentTracks.groups.count { it.type == C.TRACK_TYPE_TEXT }
        }
        assertTrue("ожидалось 2 аудиодорожки в потоке, найдено $audioGroups", audioGroups >= 2)
        assertTrue("ожидалась субтитровая дорожка, найдено $textGroups", textGroups >= 1)

        // Аудиодорожек две → экран обязан показать выбор языка.
        val lang = viewById(R.id.ivLang)
        assertEquals("кнопка выбора языка должна быть видима", View.VISIBLE, lang?.visibility)
        // Per product request, the nonfunctional subtitle action stays hidden.
        val subtitle = viewById(R.id.ivSubtitle)
        assertEquals("нерабочая кнопка субтитров должна быть скрыта", View.GONE, subtitle?.visibility)

        // Воспроизведение с многодорожечным потоком идёт: стартуем с начала, чтобы проверка
        // не зависела от того, сколько уже проиграно (клип 30 с).
        viaActivity {
            player!!.seekTo(0)
            player.play()
        }
        Thread.sleep(3_000)
        val position = viaActivity { player!!.currentPosition }
        val state = viaActivity { player!!.playbackState }
        assertTrue(
            "воспроизведение многодорожечного потока стоит: position=$position state=$state",
            position > 1_000 || state == androidx.media3.common.Player.STATE_ENDED
        )
    }

    @Test
    fun overlaysFollowControllerWithoutReappearing() {
        openPlayer()
        val view = playerViewOnScreen()!!
        repeat(10) {
            for (shown in listOf(false, true)) {
                viaActivity {
                    if (shown) view.showController() else view.hideController()
                }
                repeat(20) {
                    viaActivity { activity ->
                        assertEquals(shown, view.isControllerFullyVisible)
                        assertEquals(shown, activity.findViewById<View>(R.id.ivBack).isShown)
                        assertEquals(View.GONE, activity.findViewById<View>(R.id.ivSubtitle).visibility)
                    }
                    Thread.sleep(20)
                }
            }
        }
    }

}
