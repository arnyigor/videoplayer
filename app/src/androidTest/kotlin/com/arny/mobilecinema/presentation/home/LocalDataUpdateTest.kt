package com.arny.mobilecinema.presentation.home

import androidx.paging.PagingData
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
import com.arny.mobilecinema.domain.models.*
import com.arny.mobilecinema.presentation.DeviceGuard
import com.arny.mobilecinema.presentation.MainActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.util.concurrent.atomic.AtomicInteger

/** Read-only UI doubles: these tests never clear or import into the real Movies database. */
class LocalDataUpdateTest {
    private lateinit var originalMovies: MoviesInteractor
    private lateinit var originalUpdates: DataUpdateInteractor
    private var records = emptyList<ViewMovie>()
    private val checks = AtomicInteger(0)
    private val downloads = AtomicInteger(0)

    @Before fun fakeSources() {
        DeviceGuard.requireApprovedDevice()
        originalMovies = GlobalContext.get().get()
        originalUpdates = GlobalContext.get().get()
        val movies = object : MoviesInteractor by originalMovies {
            override suspend fun getHomeHighlights(searchAddTypes: List<String>, limit: Int) = HomeHighlights()
            override suspend fun getOrder(isHistory: Boolean) = com.arny.mobilecinema.data.repository.AppConstants.Order.SMART
            override fun isMoviesEmpty(): Flow<DataResult<Boolean>> = flowOf(DataResult.Success(records.isEmpty()))
            override fun getMovies(search: String, order: String, searchType: String, searchAddTypes: List<String>,
                genres: List<String>, countries: List<String>, years: SimpleIntRange?, imdbs: SimpleFloatRange?,
                kps: SimpleFloatRange?, likesPriority: Boolean): Flow<PagingData<ViewMovie>> = flowOf(PagingData.from(records))
        }
        val updates = object : DataUpdateInteractor by originalUpdates {
            override fun resetUpdate() = Unit
            override suspend fun intentUrl(): Flow<String> = emptyFlow()
            override suspend fun getUpdateDate(force: Boolean): Flow<DataResult<DataUpdateResult>> {
                if (!force) return emptyFlow() // Do not create a startup dialog over the control under test.
                checks.incrementAndGet()
                return flowOf(DataResult.Success(DataUpdateResult("2026.11.02 20:00", false)))
            }
            override suspend fun requestFile(force: Boolean, hasPartUpdate: Boolean) {
                assertFalse(force)
                assertFalse(hasPartUpdate)
                downloads.incrementAndGet()
            }
        }
        loadKoinModules(module {
            single<MoviesInteractor> { movies }
            single<DataUpdateInteractor> { updates }
        })
    }

    @After fun restore() {
        loadKoinModules(module {
            single<MoviesInteractor> { originalMovies }
            single<DataUpdateInteractor> { originalUpdates }
        })
    }

    @Test fun emptyStateButtonChecksAndDownloadsAfterConfirmation() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.btnUpdateList)).check(matches(isDisplayed())).perform(click())
            onView(withText(R.string.new_films_update)).check(matches(isDisplayed()))
            assertEquals(1, checks.get())
            assertEquals(0, downloads.get())
            onView(withText(android.R.string.ok)).perform(click())
            assertEquals(1, downloads.get())
        }
    }

    @Test fun updateMenuAlsoWorksWithAPartiallyPopulatedCatalog() {
        records = listOf(ViewMovie(dbId = 90000001, title = "Partial catalogue", type = MovieType.CINEMA.value))
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.rcVideoList)).check(matches(isDisplayed()))
            onView(withContentDescription(androidx.appcompat.R.string.abc_action_menu_overflow_description)).perform(click())
            onView(withText(R.string.update_list)).perform(click())
            onView(withText(R.string.new_films_update)).check(matches(isDisplayed()))
            assertEquals(1, checks.get())
            assertEquals(0, downloads.get())
            onView(withText(android.R.string.ok)).perform(click())
            assertEquals(1, downloads.get())
        }
    }
}
