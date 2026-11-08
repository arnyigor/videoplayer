package com.arny.mobilecinema.presentation.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.ViewMovie
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val movies = (1L..80L).map { ViewMovie(dbId = it, title = "Movie $it") }
    private val offsets = mutableListOf<Int>()
    private var failAppend = false
    private var models = 0
    private val interactor = object : MoviesInteractor by mock() {
        override suspend fun loadCanonicalGenres() = listOf("drama")
        override suspend fun getCatalogPage(movieType: MovieType, order: String, genres: List<String>, limit: Int, offset: Int): List<ViewMovie> {
            offsets += offset
            if (offset > 0 && failAppend) { failAppend = false; error("Transient page failure") }
            return movies.drop(offset).take(limit)
        }
    }

    @BeforeEach fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterEach fun clear() { store.clear(); Dispatchers.resetMain() }
    private fun model(state: SavedStateHandle = SavedStateHandle()) = CatalogViewModel(interactor, "FILMS", state)
        .also { store.put("catalog-${models++}", it) }

    @Test fun `append failure retries the same offset without losing loaded cards`() = runTest(dispatcher) {
        val vm = model()
        advanceUntilIdle()
        val first = vm.uiState.value.items
        failAppend = true
        vm.loadMore()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error)
        assertEquals(first, vm.uiState.value.items)
        vm.retry()
        assertEquals(first, vm.uiState.value.items)
        assertFalse(vm.uiState.value.isLoading)
        advanceUntilIdle()
        assertEquals(listOf(0, 24, 24), offsets)
        assertEquals(48, vm.uiState.value.items.size)
        assertFalse(vm.uiState.value.error)
    }

    @Test fun `filters focus and position restore with a new saved state handle`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = model(saved)
        advanceUntilIdle()
        vm.selectGenre("drama")
        vm.selectSort(CatalogSort.RATING)
        advanceUntilIdle()
        vm.saveGridScroll(30, 17)
        vm.saveFocusedTitle(40L)
        val restored = model(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        advanceUntilIdle()
        assertEquals("drama", restored.uiState.value.genre)
        assertEquals(CatalogSort.RATING, restored.uiState.value.sort)
        assertEquals(30, restored.gridFirstIndex)
        assertEquals(17, restored.gridScrollOffset)
        assertEquals(40L, restored.focusedTitleId)
        assertTrue(restored.uiState.value.items.any { it.dbId == 40L })
    }

    @Test fun `changing filters resets stale grid and focus`() = runTest(dispatcher) {
        val vm = model()
        advanceUntilIdle()
        vm.saveGridScroll(60, 20)
        vm.saveFocusedTitle(61L)
        vm.selectGenre("drama")
        advanceUntilIdle()
        assertEquals(0, vm.gridFirstIndex)
        assertEquals(0, vm.gridScrollOffset)
        assertNull(vm.focusedTitleId)
    }
}
