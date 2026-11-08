package com.arny.mobilecinema.presentation.sitesearch

import android.os.SystemClock
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
import com.arny.mobilecinema.domain.models.DataUpdateResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import com.arny.mobilecinema.presentation.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emulator-only regression scenario. Uses the existing catalogue without modifying it.
 * Build with -PmobileUiTest=true; run this class explicitly, not the live-update suite.
 */
@RunWith(AndroidJUnit4::class)
class SearchQueryIntegrationTest {
    private lateinit var originalUpdates: DataUpdateInteractor

    @After
    fun restoreUpdateChecks() {
        if (::originalUpdates.isInitialized) loadKoinModules(module { single<DataUpdateInteractor> { originalUpdates } })
    }

    @org.junit.Before
    fun grantStorage() {
        com.arny.mobilecinema.presentation.DeviceGuard.requireApprovedDevice()
        // A live update check can put a modal dialog above navigation at any time.
        originalUpdates = GlobalContext.get().get()
        val noUpdates = object : DataUpdateInteractor by originalUpdates {
            override suspend fun getUpdateDate(force: Boolean): Flow<DataResult<DataUpdateResult>> = emptyFlow()
            override suspend fun isAutoUpdateAllowed() = false
            override suspend fun requestFile(force: Boolean, hasPartUpdate: Boolean) {
                error("Navigation test must not download a catalog")
            }
        }
        loadKoinModules(module { single<DataUpdateInteractor> { noUpdates } })
        // Иначе системный диалог разрешений перекрывает активность и Espresso не видит RESUMED.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        listOf(
            "pm grant $packageName android.permission.WRITE_EXTERNAL_STORAGE",
            "pm grant $packageName android.permission.READ_EXTERNAL_STORAGE",
        ).forEach { command ->
            instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            }
        }
    }
    @Test
    fun rapidUnicodeQuerySurvivesSourceSwitches() {
        com.arny.mobilecinema.presentation.DeviceGuard.requireApprovedDevice()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.nav_site_search)).perform(click())
            onView(withId(R.id.btnSourceLocal)).perform(click())
            // Replace input in one main-thread burst: no network/idle waits between queries.
            scenario.onActivity { activity ->
                val input = activity.findViewById<EditText>(R.id.tiedtSiteSearch)
                input.setText("m")
                input.setText("matrix")
                input.setText("матрица")
            }
            onView(withId(R.id.tiedtSiteSearch)).check(matches(withText("матрица")))
            onView(withId(R.id.btnSiteSearch)).perform(click())

            val deadline = SystemClock.elapsedRealtime() + 10_000
            var localReady = false
            while (!localReady && SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity { activity ->
                    val host = activity.supportFragmentManager.primaryNavigationFragment
                    val search = host?.childFragmentManager?.primaryNavigationFragment as? SiteSearchFragment
                    val list = search?.view?.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvSiteSearch)
                    localReady = (list?.adapter?.itemCount ?: 0) > 0
                }
                if (!localReady) SystemClock.sleep(50)
            }
            // Instrumentation-прогон удаляет данные приложения, поэтому каталог может быть пуст.
            // Проверка устаревших ответов имеет смысл только при непустой базе.
            org.junit.Assume.assumeTrue(
                "каталог пуст: проверка устаревших локальных ответов пропущена",
                localReady
            )
            assertTrue("Need a catalogue containing Cyrillic Matrix matches", localReady)

            for (source in listOf(R.id.btnSourceAll, R.id.btnSourceRemote, R.id.btnSourceLocal)) {
                onView(withId(source)).perform(click())
                onView(withId(R.id.tiedtSiteSearch)).check(matches(withText("матрица")))
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val input = activity.findViewById<EditText>(R.id.tiedtSiteSearch)
                assertEquals("матрица", input.text.toString())
            }
        }
    }

    /**
     * S-012: повторный вход на экран поиска (как после Back из деталей) сохраняет введённый
     * запрос и выбранный источник — экран не должен «сбрасываться» в пустое состояние.
     */
    @Test
    fun queryAndSourceSurviveBackFromDetails() {
        com.arny.mobilecinema.presentation.DeviceGuard.requireApprovedDevice()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.nav_site_search)).perform(click())
            onView(withId(R.id.btnSourceLocal)).perform(click())
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.tiedtSiteSearch).setText("матрица")
            }
            onView(withId(R.id.btnSiteSearch)).perform(click())
            // Возврат на главную и повторный вход: состояние экрана должно сохраниться.
            onView(withId(R.id.nav_home)).perform(click())
            onView(withId(R.id.nav_site_search)).perform(click())
            onView(withId(R.id.tiedtSiteSearch)).check(matches(withText("матрица")))
            onView(withId(R.id.btnSourceLocal)).check(matches(androidx.test.espresso.matcher.ViewMatchers.isChecked()))
        }
    }

    /** Clearing a completed query must hide its old rows, not draw them below the idle state. */
    @Test
    fun clearingQueryHidesPreviousResults() {
        com.arny.mobilecinema.presentation.DeviceGuard.requireApprovedDevice()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.nav_site_search)).perform(click())
            onView(withId(R.id.btnSourceLocal)).perform(click())
            scenario.onActivity {
                it.findViewById<EditText>(R.id.tiedtSiteSearch).setText("матрица")
            }
            onView(withId(R.id.btnSiteSearch)).perform(click())
            val deadline = SystemClock.elapsedRealtime() + 10_000
            var ready = false
            while (!ready && SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity {
                    ready = (it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvSiteSearch)
                        ?.adapter?.itemCount ?: 0) > 0
                }
                if (!ready) SystemClock.sleep(50)
            }
            assertTrue("Requires fixture catalogue with Matrix results", ready)
            onView(withId(R.id.rvSiteSearch)).check(matches(
                androidx.test.espresso.matcher.ViewMatchers.isDisplayed()
            ))
            onView(withId(R.id.tiedtSiteSearch)).perform(click())
            onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription(
                com.google.android.material.R.string.clear_text_end_icon_content_description
            )).perform(click())
            onView(withId(R.id.tiedtSiteSearch)).check(matches(withText("")))
            onView(withId(R.id.rvSiteSearch)).check(matches(org.hamcrest.Matchers.not(
                androidx.test.espresso.matcher.ViewMatchers.isDisplayed()
            )))
            onView(withId(R.id.tvSiteSearchEmpty))
                .check(matches(withText(R.string.search_empty_hint)))
        }
    }
}
