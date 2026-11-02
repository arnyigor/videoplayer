package com.arny.mobilecinema.presentation.a11y

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EdgeToEdgeInsetsTest {
    @Test
    fun safeInsetsAppliedOnceInPortraitLandscapeAndWithKeyboard() {
        DeviceGuard.requireApprovedDevice()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pkg = instrumentation.targetContext.packageName
        listOf("READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE").forEach { permission ->
            instrumentation.uiAutomation.executeShellCommand("pm grant $pkg android.permission.$permission")
                .use { fd -> android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() } }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fun checkInsets() {
                scenario.onActivity { activity ->
                    val root = (activity.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)
                    val insets = ViewCompat.getRootWindowInsets(root)!!
                    val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
                    val location = IntArray(2)
                    root.getLocationOnScreen(location)
                    assertEquals("root must draw from top edge", 0, location[1])
                    assertEquals(safe.left, root.paddingLeft)
                    assertEquals(safe.top, root.paddingTop)
                    assertEquals(safe.right, root.paddingRight)
                    assertEquals(safe.bottom, root.paddingBottom)
                    // Material BottomNavigationView must not apply navigation bar padding again.
                    assertEquals(0, activity.findViewById<View>(R.id.bottom_nav_view).paddingBottom)
                }
            }
            Thread.sleep(2_000)
            checkInsets()
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            Thread.sleep(2_000)
            checkInsets()
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            Thread.sleep(2_000)
            scenario.onActivity { activity ->
                val nav = (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
                nav.navigate(R.id.nav_site_search)
            }
            Thread.sleep(1_000)
            scenario.onActivity { activity ->
                val input = activity.findViewById<View>(R.id.tiedtSiteSearch)
                input.requestFocus()
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
            Thread.sleep(2_000)
            checkInsets()
            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(android.R.id.content)
                assertTrue("keyboard must actually be visible", ViewCompat.getRootWindowInsets(root)!!
                    .isVisible(WindowInsetsCompat.Type.ime()))
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(root.windowToken, 0)
            }
            Thread.sleep(1_000)
            scenario.onActivity { activity ->
                val nav = (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
                val movie = Movie(
                    dbId = 9011, movieId = 9011, title = "Fixture insets",
                    type = MovieType.CINEMA, info = MovieInfo(),
                    cinemaUrlData = CinemaUrlData(hdUrl = AnwapUrl(urls = listOf("http://127.0.0.1:8765/test.mp4"))),
                )
                nav.navigate(R.id.nav_player_view, Bundle().apply {
                    putString("path", "http://127.0.0.1:8765/test.mp4")
                    putParcelable("movie", movie)
                    putString("playId", "insets-check")
                })
            }
            Thread.sleep(3_000)
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                assertEquals(0, root.paddingLeft)
                assertEquals(0, root.paddingTop)
                assertEquals(0, root.paddingRight)
                assertEquals(0, root.paddingBottom)
                val nav = (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
                assertEquals(R.id.nav_player_view, nav.currentDestination!!.id)
                nav.popBackStack()
            }
            Thread.sleep(2_000)
            checkInsets()
        }
    }
}
