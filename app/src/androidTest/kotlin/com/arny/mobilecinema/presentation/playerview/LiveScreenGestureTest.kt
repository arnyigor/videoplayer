package com.arny.mobilecinema.presentation.playerview

import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S-011 / F-019: двойной тап на **живом экране приложения** — настоящий PlayerViewFragment,
 * настоящая разметка f_player_view.xml (DoubleTapPlayerView + YouTubeOverlay из Media3-форка),
 * настоящий Media3 player.
 *
 * Почему так: adb `input tap` на CPU-эмуляторе не укладывается в окно двойного тапа
 * (~610 мс между вызовами), поэтому касания синтезируются на главном потоке. Каталог/БД не
 * нужны: экран плеера открывается напрямую через NavController с реальным URL фикстуры.
 */
@RunWith(AndroidJUnit4::class)
class LiveScreenGestureTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<*>? = null

    private val movie = Movie(
        dbId = 9001,
        movieId = 9001,
        title = "Fixture live gesture",
        type = MovieType.CINEMA,
        info = MovieInfo(),
        cinemaUrlData = CinemaUrlData(hdUrl = AnwapUrl(urls = listOf("http://127.0.0.1:8765/test.mp4"))),
    )

    @Before
    fun setUp() {
        DeviceGuard.requireApprovedDevice()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        // Иначе системный диалог разрешений перекрывает активность.
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

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /** Чтение с активности: onActivity сам выполняет блок на main, поэтому runOnMainSync не нужен. */
    private fun <T> viaActivity(block: (androidx.appcompat.app.AppCompatActivity) -> T): T {
        var result: T? = null
        scenario!!.onActivity { activity ->
            result = block(activity as androidx.appcompat.app.AppCompatActivity)
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun navController(): NavController = viaActivity { activity ->
        val host = activity.supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        host.navController
    }

    private fun openPlayer() {
        val args = Bundle().apply {
            putString("path", "http://127.0.0.1:8765/test.mp4")
            putParcelable("movie", movie)
            putString("playId", "live-gesture")
        }
        val controller = navController()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller.navigate(R.id.nav_player_view, args)
        }
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
    fun doubleTapOnLivePlayerSeeksForward() {
        openPlayer()
        Thread.sleep(6_000)
        val view = playerViewOnScreen()
        assertTrue("на экране нет DoubleTapPlayerView из Media3-форка", view != null)
        val player = onMain { view!!.player }
        assertTrue("плеер экрана не инициализирован", player != null)

        onMain { player!!.seekTo(1_000) }
        Thread.sleep(2_000)
        val before = onMain { player!!.currentPosition }

        val base = SystemClock.uptimeMillis()
        onMain {
            val x = view!!.width * 0.8f
            val y = view.height * 0.5f
            listOf(
                MotionEvent.obtain(base, base, MotionEvent.ACTION_DOWN, x, y, 0),
                MotionEvent.obtain(base, base + 40, MotionEvent.ACTION_UP, x, y, 0),
                MotionEvent.obtain(base, base + 120, MotionEvent.ACTION_DOWN, x, y, 0),
                MotionEvent.obtain(base, base + 160, MotionEvent.ACTION_UP, x, y, 0),
            ).forEach {
                view.dispatchTouchEvent(it)
                it.recycle()
            }
        }
        Thread.sleep(1_500)
        val after = onMain { player!!.currentPosition }
        assertTrue(
            "двойной тап на живом экране не перемотал вперёд: before=$before after=$after",
            after - before >= 8_000
        )
    }
}
