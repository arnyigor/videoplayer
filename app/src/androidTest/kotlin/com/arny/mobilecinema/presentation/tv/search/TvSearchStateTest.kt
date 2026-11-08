package com.arny.mobilecinema.presentation.tv.search

import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arny.mobilecinema.R
import com.arny.mobilecinema.presentation.DeviceGuard
import com.arny.mobilecinema.presentation.tv.TvMainActivity
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Search is a root section: opening a result and coming back must return to the same query,
 * filters and result list instead of a blank search screen.
 */
@RunWith(AndroidJUnit4::class)
class TvSearchStateTest {
    private var scenario: ActivityScenario<TvMainActivity>? = null

    @Before fun launch() {
        DeviceGuard.requireApprovedDevice()
        scenario = ActivityScenario.launch(TvMainActivity::class.java)
    }

    @After fun close() = scenario?.close() ?: Unit

    private fun <T> onActivity(block: (TvMainActivity) -> T): T {
        var result: T? = null
        scenario!!.onActivity { result = block(it) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun navController(activity: TvMainActivity) =
        (activity.supportFragmentManager.findFragmentById(R.id.tv_nav_host_fragment) as NavHostFragment).navController

    private fun waitFor(activity: TvMainActivity, id: Int, timeoutMs: Long = 20_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (navController(activity).currentDestination?.id == id) return
            Thread.sleep(100)
        }
        fail("Destination $id was not reached, current=${navController(activity).currentDestination?.id}")
    }

    private fun currentSearch(): TvSiteSearchFragment? = onActivity { activity ->
        var found: TvSiteSearchFragment? = null
        activity.supportFragmentManager.fragments.forEach { root ->
            if (root is TvSiteSearchFragment) found = root
            found = found ?: root.childFragmentManager?.fragments?.filterIsInstance<TvSiteSearchFragment>()?.firstOrNull()
        }
        found
    }

    @Test fun queryAndResultsSurviveOpeningDetailsAndComingBack() {
        onActivity { activity ->
            navController(activity).navigate(R.id.tvSiteSearchFragment)
            activity
        }
        waitFor(onActivity { it }, R.id.tvSiteSearchFragment)

        val search = requireNotNull(currentSearch()) { "Search fragment is missing" }
        onActivity { activity ->
            // Same entry point the Leanback search bar uses.
            search.setSearchQuery("матрица", true)
            activity
        }

        val resultsReady = run {
            val deadline = System.currentTimeMillis() + 25_000
            var size = 0
            while (System.currentTimeMillis() < deadline && size == 0) {
                size = onActivity { currentSearch()?.getResultsAdapter()?.size() ?: 0 }
                if (size == 0) Thread.sleep(300)
            }
            size
        }
        assumeTrue("Fixture catalogue has no matches for the probe query", resultsReady > 0)

        // Fixture catalogue contains "Матрица" as dbId 1; only the navigation round-trip is under test.
        val firstId = 1L

        onActivity { activity ->
            navController(activity).navigate(TvSiteSearchFragmentDirections.actionToDetails(firstId))
            activity
        }
        waitFor(onActivity { it }, R.id.tvDetailsFragment, 25_000)

        onActivity { activity ->
            assertTrue("Back from details did not return to search", navController(activity).popBackStack())
            activity
        }
        waitFor(onActivity { it }, R.id.tvSiteSearchFragment)

        val restored = requireNotNull(currentSearch()) { "Search entry was destroyed on back" }
        val editor = onActivity { restored.view?.findViewById<android.widget.TextView>(androidx.leanback.R.id.lb_search_text_editor)?.text?.toString() }
        assertTrue("Query was lost after returning from details: '$editor'", editor.orEmpty().contains("матрица", ignoreCase = true))
        assertTrue("Results were lost after returning from details",
            onActivity { restored.getResultsAdapter().size() } > 0)
    }
}
