package com.arny.mobilecinema.presentation.sitesearch

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arny.mobilecinema.R
import com.arny.mobilecinema.presentation.DeviceGuard
import com.arny.mobilecinema.presentation.MainActivity
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Native M3 sheet regression: draft restoration, apply/reset and remote-only scope.
 * The year-count assertion uses the isolated 1000-title perf_fixtures catalogue (2000..2024).
 */
@RunWith(AndroidJUnit4::class)
class SearchFiltersSheetIntegrationTest {
    @Before
    fun grantStorage() {
        DeviceGuard.requireApprovedDevice()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pkg = instrumentation.targetContext.packageName
        listOf("READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE").forEach { permission ->
            instrumentation.uiAutomation.executeShellCommand("pm grant $pkg android.permission.$permission")
                .use { fd -> android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() } }
        }
    }

    @Test
    fun draftSurvivesRecreationAndApplyResetKeepSortConsistent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.nav_site_search)).perform(click())
            onView(withId(R.id.btnSearchFilters)).perform(click())
            onView(withId(R.id.btnKindCinema)).perform(click())
            onView(withId(R.id.actSort)).perform(click())
            onView(withText(R.string.search_sort_rating)).inRoot(isPlatformPopup()).perform(click())
            onView(withId(R.id.tiedtYearFrom)).perform(click(), replaceText("2000"))
            onView(withId(R.id.tiedtYearTo)).perform(click(), replaceText("2024"))
            closeSoftKeyboard()

            scenario.recreate()
            onView(withId(R.id.btnKindCinema)).check(matches(isChecked()))
            onView(withId(R.id.tiedtYearFrom)).check(matches(withText("2000")))
            onView(withId(R.id.tiedtYearTo)).check(matches(withText("2024")))
            onView(withId(R.id.actSort)).check(matches(withText(R.string.search_sort_rating)))
            onView(withId(R.id.btnFiltersApply)).perform(click())
            onView(withId(R.id.btnSearchFilters))
                .check(matches(withContentDescription(context.getString(R.string.search_filters_count, 2))))
                .check(matches(withText("2")))

            onView(withId(R.id.btnSearchFilters)).perform(click())
            onView(withId(R.id.btnFiltersReset)).perform(click())
            onView(withId(R.id.btnKindAll)).check(matches(isChecked()))
            onView(withId(R.id.tiedtYearFrom)).check(matches(withText("")))
            onView(withId(R.id.tiedtYearTo)).check(matches(withText("")))
            // SearchFilters.reset() preserves sorting; the UI must show that same value.
            onView(withId(R.id.actSort)).check(matches(withText(R.string.search_sort_rating)))
            onView(withId(R.id.btnFiltersApply)).perform(click())
            onView(withId(R.id.btnSearchFilters)).check(matches(withContentDescription(R.string.search_filters)))
        }
    }

    @Test
    fun dismissedDraftDoesNotApplyAndRemoteSourceDisablesFilters() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.nav_site_search)).perform(click())
            onView(withId(R.id.btnSearchFilters)).perform(click())
            onView(withId(R.id.btnKindSerial)).perform(click())
            pressBack()
            onView(withId(R.id.btnSearchFilters)).check(matches(withContentDescription(R.string.search_filters)))
            onView(withId(R.id.btnSearchFilters)).perform(click())
            onView(withId(R.id.btnKindAll)).check(matches(isChecked()))
            pressBack()
            onView(withId(R.id.btnSourceRemote)).perform(click())
            onView(withId(R.id.btnSearchFilters)).check(matches(not(isEnabled())))
            onView(withId(R.id.tvFiltersNotice))
                .check(matches(withText(R.string.search_filters_disabled_for_site)))
        }
    }
}
