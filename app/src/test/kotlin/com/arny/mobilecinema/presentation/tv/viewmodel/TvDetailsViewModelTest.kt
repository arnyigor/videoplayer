package com.arny.mobilecinema.presentation.tv.viewmodel

import androidx.lifecycle.ViewModelStore
import com.arny.mobilecinema.data.db.daos.PlaybackProgressDao
import com.arny.mobilecinema.data.db.models.PlaybackProgressEntity
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.repository.playback.PlaybackProgressStore
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.playback.NextEpisodeResolver
import com.arny.mobilecinema.domain.playback.testEpisode
import com.arny.mobilecinema.domain.playback.testSeason
import com.arny.mobilecinema.domain.playback.testSeries
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvDetailsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val series = testSeries(listOf(
        testSeason(1, testEpisode(11, "1")), testSeason(2, testEpisode(21, "1")),
    ))
    private val interactor = object : MoviesInteractor by mock() {
        override fun getMovie(id: Long) = flowOf(DataResult.Success(series))
        override fun isFavorite(movieId: Long) = flowOf(DataResult.Success(false))
    }
    private val progressDao = object : PlaybackProgressDao by mock() {
        override fun findAllForMovie(profileId: String, movieDbId: Long): List<PlaybackProgressEntity> = emptyList()
    }
    private fun model() = TvDetailsViewModel(
        interactor, PlaybackProgressStore(progressDao, mock(), dispatcher = dispatcher), NextEpisodeResolver()
    ).also { store.put("tv-details", it) }

    @BeforeEach fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterEach fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun `season selection emits a new observed state`() = runTest(dispatcher) {
        val model = model()
        model.loadMovie(series.dbId)
        advanceUntilIdle()
        val observed = mutableListOf<Int>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.uiState.collect { observed += it.selectedSeasonIndex }
        }
        model.selectSeason(1)
        runCurrent()
        assertEquals(listOf(0, 1), observed)
        assertEquals(1, model.selectedSeasonIndex)
    }

    @Test fun `refreshing details keeps the selected season`() = runTest(dispatcher) {
        val model = model()
        model.loadMovie(series.dbId)
        advanceUntilIdle()
        model.selectSeason(1)
        model.retry()
        advanceUntilIdle()
        assertEquals(1, model.uiState.value.selectedSeasonIndex)
    }

    @Test fun `season selection stays within available range`() = runTest(dispatcher) {
        val model = model()
        model.loadMovie(series.dbId)
        advanceUntilIdle()
        model.selectSeason(99)
        assertEquals(1, model.uiState.value.selectedSeasonIndex)
        model.selectSeason(-1)
        assertEquals(0, model.uiState.value.selectedSeasonIndex)
    }
}
