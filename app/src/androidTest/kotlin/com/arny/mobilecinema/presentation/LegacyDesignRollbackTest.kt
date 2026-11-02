package com.arny.mobilecinema.presentation

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arny.mobilecinema.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** CPU emulator with scripts/perf_fixtures.py data; does not change stored user data. */
@RunWith(AndroidJUnit4::class)
class LegacyDesignRollbackTest {
    private fun waitFor(check: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!check()) {
            assertTrue("Timed out waiting for the legacy UI", SystemClock.elapsedRealtime() < deadline)
            SystemClock.sleep(50)
        }
    }

    private fun screenshot(name: String) {
        val ins = InstrumentationRegistry.getInstrumentation()
        val bitmap = ins.uiAutomation.takeScreenshot()
        java.io.File(ins.targetContext.getExternalFilesDir(null), "rollback-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun legacyHomeAndDetailsKeepTypedActorSearchAndBack() {
        DeviceGuard.requireApprovedDevice()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor {
                var ready = false
                scenario.onActivity { activity ->
                    val recycler = activity.findViewById<RecyclerView>(R.id.rcVideoList)
                    ready = recycler?.isShown == true && (recycler.adapter?.itemCount ?: 0) > 0
                }
                ready
            }
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.composeHome).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.catalogHost).visibility)
                assertTrue(activity.findViewById<View>(R.id.topGradient).isShown)
            }
            screenshot("home")
            scenario.onActivity { activity ->
                val nav = (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
                nav.navigate(R.id.nav_details, Bundle().apply { putLong("id", 1L) })
            }
            waitFor {
                var ready = false
                scenario.onActivity { activity ->
                    ready = activity.findViewById<TextView>(R.id.tvTitle)?.text?.toString() == "Матрица"
                }
                ready
            }
            SystemClock.sleep(300)
            screenshot("details")
            scenario.onActivity { activity ->
                fun actor(view: View): View? {
                    if (view is TextView && view.text.toString() == "Fixture Actor") return view
                    if (view is ViewGroup) for (i in 0 until view.childCount) actor(view.getChildAt(i))?.let { return it }
                    return null
                }
                val chip = actor(activity.findViewById(android.R.id.content))
                assertNotNull(chip)
                chip!!.performClick()
            }
            waitFor {
                var ready = false
                scenario.onActivity { activity ->
                    ready = (activity.findViewById<RecyclerView>(R.id.rvSiteSearch)?.adapter?.itemCount ?: 0) > 0
                }
                ready
            }
            screenshot("actor-search")
            scenario.onActivity { activity ->
                val nav = (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
                assertEquals("actors", nav.currentBackStackEntry?.arguments?.getString("searchType"))
                assertTrue(nav.navigateUp())
                assertEquals(R.id.nav_details, nav.currentDestination?.id)
                assertEquals(1L, nav.currentBackStackEntry?.arguments?.getLong("id"))
            }
        }
    }
}
