package com.arny.mobilecinema.presentation.tv

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arny.mobilecinema.R
import com.arny.mobilecinema.presentation.DeviceGuard
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Rail switching must keep each root's ViewModel state instead of destroying it. */
@RunWith(AndroidJUnit4::class)
class TvRootStateTest {
    private var scenario: ActivityScenario<TvMainActivity>? = null

    class Marker : ViewModel() { var value = 0 }

    @Before fun launch() {
        DeviceGuard.requireApprovedDevice()
        scenario = ActivityScenario.launch(TvMainActivity::class.java)
    }

    @After fun close() { scenario?.close() }

    private fun markerOf(fragment: Fragment): Marker =
        ViewModelProvider(fragment, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = Marker() as T
        }).get("root-marker", Marker::class.java)

    private fun <T> onActivity(block: (TvMainActivity) -> T): T {
        var result: T? = null
        scenario!!.onActivity { result = block(it) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun currentFragment(): Fragment = onActivity {
        (it.supportFragmentManager.findFragmentById(R.id.tv_nav_host_fragment)
            as androidx.navigation.fragment.NavHostFragment).childFragmentManager.primaryNavigationFragment!!
    }

    private fun waitFor(name: String): Fragment {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            onActivity { }
            val fragment = currentFragment()
            if (fragment.javaClass.simpleName == name) return fragment
            Thread.sleep(200)
        }
        fail("Fragment $name was not reached, current=${currentFragment().javaClass.simpleName}")
        error("unreachable")
    }

    @Test fun returningToSearchRootKeepsItsViewModelState() {
        onActivity { it.findViewById<android.view.View>(R.id.tvNavSearch).performClick() }
        val first = waitFor("TvSiteSearchFragment")
        markerOf(first).value = 7
        onActivity { it.findViewById<android.view.View>(R.id.tvNavFilms).performClick() }
        waitFor("TvCatalogFragment")
        onActivity { it.findViewById<android.view.View>(R.id.tvNavSearch).performClick() }
        val second = waitFor("TvSiteSearchFragment")
        assertEquals("Rail switching discarded the search root state", 7, markerOf(second).value)
    }
}
