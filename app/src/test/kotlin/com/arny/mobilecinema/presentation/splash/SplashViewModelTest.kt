package com.arny.mobilecinema.presentation.splash

import androidx.lifecycle.ViewModelStore
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @BeforeEach fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterEach fun cleanup() { store.clear(); Dispatchers.resetMain() }

    private fun model(interactor: DataUpdateInteractor) = SplashViewModel(interactor).also {
        store.put("splash", it)
    }

    @Test fun `ready is published without an animation timer and retains the shared URL`() = runTest(dispatcher) {
        val interactor = object : DataUpdateInteractor by mock() {
            override suspend fun checkBaseUrl() = flowOf(DataResult.Success(true))
        }
        val vm = model(interactor)
        vm.onIntentUrl("https://mm.anwap.media/films/42")
        assertEquals(SplashState.Checking, vm.state.value)
        runCurrent()
        assertEquals(0L, currentTime)
        assertEquals(SplashState.Ready, vm.state.value)
        assertTrue(vm.readyComplete.value)
        assertEquals("https://mm.anwap.media/films/42", vm.sharedUrl)
    }

    @Test fun `unavailable mirror does not leave startup waiting`() = runTest(dispatcher) {
        val interactor = object : DataUpdateInteractor by mock() {
            override suspend fun checkBaseUrl() = flowOf(DataResult.Success(false))
        }
        val vm = model(interactor)
        runCurrent()
        assertEquals(SplashState.SiteUnavailable, vm.state.value)
        assertFalse(vm.readyComplete.value)
    }

    @Test fun `network errors also finish startup`() = runTest(dispatcher) {
        val interactor = object : DataUpdateInteractor by mock() {
            override suspend fun checkBaseUrl() = flowOf(DataResult.Error(IllegalStateException("offline")))
        }
        val vm = model(interactor)
        runCurrent()
        assertEquals(SplashState.SiteUnavailable, vm.state.value)
    }

    @Test fun `thrown resolver error finishes startup`() = runTest(dispatcher) {
        val interactor = object : DataUpdateInteractor by mock() {
            override suspend fun checkBaseUrl() = flow<DataResult<Boolean>> { error("resolver failed") }
        }
        val vm = model(interactor)
        runCurrent()
        assertEquals(SplashState.SiteUnavailable, vm.state.value)
    }
}
