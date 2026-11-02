package com.arny.mobilecinema.presentation.home

import androidx.lifecycle.ViewModelStore
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
import com.arny.mobilecinema.domain.models.DataUpdateResult
import com.arny.mobilecinema.domain.models.HomeHighlights
import com.arny.mobilecinema.presentation.uimodels.AlertType
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeUpdateTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val store = ViewModelStore()
    @BeforeEach fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterEach fun clear() { store.clear(); Dispatchers.resetMain() }
    private fun model(updates: DataUpdateInteractor): HomeViewModel = HomeViewModel(updates,
        object : MoviesInteractor by mock() {
            override suspend fun getHomeHighlights(searchAddTypes: List<String>, limit: Int) = HomeHighlights()
        },
    ).also { store.put("home", it) }

    @Test fun `forced manual check asks for confirmation instead of silently starting an automatic update`() = runTest(dispatcher) {
        var forced = false
        var downloads = 0
        val updates = object : DataUpdateInteractor by mock() {
            override suspend fun getUpdateDate(force: Boolean): Flow<DataResult<DataUpdateResult>> {
                forced = force
                return flowOf(DataResult.Success(DataUpdateResult("2026.11.02 20:00", false)))
            }
            override suspend fun isAutoUpdateAllowed(): Boolean = error("Manual checks must not take the automatic path")
            override suspend fun requestFile(force: Boolean, hasPartUpdate: Boolean) {
                assertFalse(force)
                assertFalse(hasPartUpdate, "Incomplete local data requires the full archive, not data_0")
                downloads++
            }
        }
        val vm = model(updates)
        vm.downloadData(force = true)
        val alert = vm.alert.first()
        assertTrue(forced)
        assertEquals(0, downloads)
        assertEquals(AlertType.Update(false, false), alert.type)
        vm.onConfirmAlert(alert.type)
        assertEquals(1, downloads)
    }

    @Test fun `repeated manual checks do not start additional intent observers`() = runTest(dispatcher) {
        var observers = 0
        val urls = MutableSharedFlow<String>()
        val updates = object : DataUpdateInteractor by mock() {
            override suspend fun intentUrl(): Flow<String> = flow {
                observers++
                emitAll(urls)
            }
        }
        val vm = model(updates)
        repeat(3) { vm.checkIntent() }
        assertEquals(1, observers)
    }
}
