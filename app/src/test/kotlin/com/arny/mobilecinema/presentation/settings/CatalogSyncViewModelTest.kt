package com.arny.mobilecinema.presentation.settings

import androidx.lifecycle.ViewModelStore
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.update.CatalogUpdatePhase
import com.arny.mobilecinema.domain.interactors.update.CatalogUpdateTracker
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
import com.arny.mobilecinema.domain.models.DataUpdateResult
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogSyncViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val tracker = CatalogUpdateTracker()
    private var models = 0
    private var downloads = 0
    private var fullUpdates = 0
    private var cancellations = 0
    private var failDispatch = false
    private val interactor = object : DataUpdateInteractor by mock() {
        override suspend fun getUpdateDate(force: Boolean) =
            flowOf(DataResult.Success(DataUpdateResult("2026.11.02 20:00", true)))
        override suspend fun requestFile(force: Boolean, hasPartUpdate: Boolean) {
            assertFalse(force)
            assertTrue(hasPartUpdate)
            downloads++
            if (failDispatch) error("Service start failed")
        }
        override fun updateAll() {
            fullUpdates++
            if (failDispatch) error("Service start failed")
        }
        override fun cancelUpdate() { cancellations++ }
    }

    @BeforeEach fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterEach fun clear() { store.clear(); Dispatchers.resetMain() }
    private fun model() = CatalogSyncViewModel(interactor, tracker).also { store.put("sync-${models++}", it) }

    @Test fun `dispatching download remains busy until executor confirms completion`() = runTest(dispatcher) {
        val vm = model()
        vm.check()
        advanceUntilIdle()
        vm.loadUpdate()
        advanceUntilIdle()
        assertEquals(1, downloads)
        assertTrue(vm.state.value.isLoading)
        assertEquals(CatalogUpdatePhase.QUEUED, vm.state.value.phase)
        tracker.update(CatalogUpdatePhase.IMPORTING, 57)
        advanceUntilIdle()
        assertEquals(57, vm.state.value.progress)
        tracker.update(CatalogUpdatePhase.SUCCEEDED, processedMovies = 1200)
        advanceUntilIdle()
        assertFalse(vm.state.value.isLoading)
        assertEquals(SyncResult.SUCCEEDED, vm.state.value.lastResult)
        assertEquals(1200, vm.state.value.processedMovies)
    }

    @Test fun `new settings model sees running operation and retained terminal result`() = runTest(dispatcher) {
        tracker.update(CatalogUpdatePhase.DOWNLOADING, 34)
        val reopened = model()
        assertTrue(reopened.state.value.isLoading)
        assertEquals(34, reopened.state.value.progress)
        tracker.update(CatalogUpdatePhase.FAILED)
        advanceUntilIdle()
        val afterFailure = model()
        assertFalse(afterFailure.state.value.isLoading)
        assertEquals(SyncResult.FAILED, afterFailure.state.value.lastResult)
        assertTrue(afterFailure.state.value.error)
    }

    @Test fun `full update reserves operation immediately and duplicate calls do not dispatch`() = runTest(dispatcher) {
        val vm = model()
        vm.fullUpdate()
        vm.fullUpdate()
        val reopened = model()
        reopened.fullUpdate()
        assertEquals(1, fullUpdates)
        assertTrue(vm.state.value.isLoading)
        assertTrue(reopened.state.value.isLoading)
    }

    @Test fun `cancellation request stays busy until executor acknowledges it`() = runTest(dispatcher) {
        val vm = model()
        vm.fullUpdate()
        vm.cancelUpdate()
        assertEquals(1, cancellations)
        assertTrue(vm.state.value.isLoading)
        tracker.update(CatalogUpdatePhase.CANCELLED)
        advanceUntilIdle()
        assertFalse(vm.state.value.isLoading)
        assertEquals(SyncResult.CANCELLED, vm.state.value.lastResult)
    }

    @Test fun `dispatch failure releases queue and displays error`() = runTest(dispatcher) {
        failDispatch = true
        val vm = model()
        vm.fullUpdate()
        advanceUntilIdle()
        assertFalse(tracker.status.value.isRunning)
        assertEquals(SyncResult.FAILED, vm.state.value.lastResult)
        assertTrue(vm.state.value.error)
    }

    @Test fun `failed download dispatch releases queue`() = runTest(dispatcher) {
        failDispatch = true
        val vm = model()
        vm.check()
        advanceUntilIdle()
        vm.loadUpdate()
        advanceUntilIdle()
        assertFalse(vm.state.value.isLoading)
        assertEquals(SyncResult.FAILED, vm.state.value.lastResult)
        assertFalse(tracker.status.value.isRunning)
    }

    @Test fun `repeated check is reserved before coroutine starts`() = runTest(dispatcher) {
        val vm = model()
        vm.check()
        vm.check()
        assertTrue(vm.state.value.isChecking)
        advanceUntilIdle()
        assertFalse(vm.state.value.isChecking)
        assertEquals(SyncResult.UPDATE_AVAILABLE, vm.state.value.lastResult)
    }

    @Test fun `tracker resets progress between operations and normalizes unknown progress`() {
        assertTrue(tracker.tryQueue())
        assertFalse(tracker.tryQueue())
        tracker.update(CatalogUpdatePhase.IMPORTING, 130)
        assertEquals(100, tracker.status.value.progress)
        tracker.update(CatalogUpdatePhase.SUCCEEDED, processedMovies = 1000)
        assertTrue(tracker.tryQueue())
        assertNull(tracker.status.value.progress)
        assertNull(tracker.status.value.processedMovies)
        tracker.update(CatalogUpdatePhase.SCRAPING, -1)
        assertNull(tracker.status.value.progress)
    }
}
