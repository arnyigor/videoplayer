package com.arny.mobilecinema.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.update.CatalogUpdatePhase
import com.arny.mobilecinema.domain.interactors.update.CatalogUpdateStatus
import com.arny.mobilecinema.domain.interactors.update.CatalogUpdateTracker
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/** Service-owned progress survives leaving settings and creating another ViewModel. */
data class CatalogSyncState(
    val isChecking: Boolean = false,
    val isLoading: Boolean = false,
    val updateDate: String? = null,
    val hasPartUpdate: Boolean = false,
    val error: Boolean = false,
    val lastResult: SyncResult = SyncResult.NONE,
    val phase: CatalogUpdatePhase = CatalogUpdatePhase.IDLE,
    val progress: Int? = null,
    val processedMovies: Int? = null,
)

enum class SyncResult {
    NONE, CHECKED_NO_UPDATE, UPDATE_AVAILABLE, LOADING_STARTED, LOADING_IN_PROGRESS, FAILED,
    SUCCEEDED, CANCELLED,
}

class CatalogSyncViewModel(
    private val interactor: DataUpdateInteractor,
    private val updates: CatalogUpdateTracker = CatalogUpdateTracker(),
) : ViewModel() {
    private val initialStatus = updates.status.value
    private val _state = MutableStateFlow(applyStatus(CatalogSyncState(), initialStatus))
    val state = _state.asStateFlow()
    private var checkJob: Job? = null

    init {
        viewModelScope.launch {
            var observed = initialStatus
            updates.status.collect { status ->
                if (status != observed) {
                    observed = status
                    _state.update { applyStatus(it, status) }
                }
            }
        }
    }

    /** Read-only update availability check, not the catalog executor. */
    fun check() {
        if (_state.value.isChecking || updates.status.value.isRunning) return
        _state.update { it.copy(isChecking = true, error = false) }
        checkJob = viewModelScope.launch {
            try {
                interactor.getUpdateDate(force = true).collect { result ->
                    if (updates.status.value.isRunning) return@collect
                    when (result) {
                        is DataResult.Success -> {
                            val date = result.result.updateDateTime
                            _state.update {
                                it.copy(
                                    isChecking = false,
                                    updateDate = date.takeIf(String::isNotBlank),
                                    hasPartUpdate = result.result.hasPartUpdate,
                                    lastResult = if (date.isBlank()) SyncResult.CHECKED_NO_UPDATE else SyncResult.UPDATE_AVAILABLE,
                                )
                            }
                        }
                        is DataResult.Error -> {
                            if (result.throwable is CancellationException) throw result.throwable
                            Timber.e(result.throwable, "catalog check failed")
                            _state.update { it.copy(error = true, lastResult = SyncResult.FAILED) }
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.e(error, "catalog check failed")
                _state.update { it.copy(error = true, lastResult = SyncResult.FAILED) }
            } finally {
                _state.update { it.copy(isChecking = false) }
            }
        }
    }

    fun loadUpdate() {
        val selected = _state.value
        if (selected.updateDate == null || !queue()) return
        // Start dispatch immediately, so leaving the screen cannot abandon a queued command.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                interactor.requestFile(force = false, hasPartUpdate = selected.hasPartUpdate)
                // A returned startService call is not a completed download/import.
            } catch (error: CancellationException) {
                if (updates.status.value.phase == CatalogUpdatePhase.QUEUED) {
                    updates.update(CatalogUpdatePhase.CANCELLED)
                }
                throw error
            } catch (error: Exception) {
                Timber.e(error, "catalog load failed")
                updates.update(CatalogUpdatePhase.FAILED)
            }
        }
    }

    fun fullUpdate() {
        if (!queue()) return
        try {
            interactor.updateAll()
        } catch (error: Exception) {
            Timber.e(error, "catalog full update failed")
            updates.update(CatalogUpdatePhase.FAILED)
        }
    }

    fun cancelUpdate() {
        if (!updates.status.value.isRunning) return
        try {
            interactor.cancelUpdate()
            // Only the executor can confirm that the operation was cancelled.
        } catch (error: Exception) {
            Timber.e(error, "catalog cancellation request failed")
            _state.update { it.copy(error = true) }
        }
    }

    private fun queue(): Boolean {
        if (_state.value.isChecking || !updates.tryQueue()) return false
        checkJob?.cancel()
        _state.update { applyStatus(it, updates.status.value) }
        return true
    }

    private fun applyStatus(state: CatalogSyncState, status: CatalogUpdateStatus): CatalogSyncState {
        if (status.phase == CatalogUpdatePhase.IDLE) return state
        return state.copy(
            isChecking = false,
            isLoading = status.isRunning,
            updateDate = null,
            error = status.phase == CatalogUpdatePhase.FAILED,
            lastResult = when (status.phase) {
                CatalogUpdatePhase.SUCCEEDED -> SyncResult.SUCCEEDED
                CatalogUpdatePhase.FAILED -> SyncResult.FAILED
                CatalogUpdatePhase.CANCELLED, CatalogUpdatePhase.INTERRUPTED -> SyncResult.CANCELLED
                else -> SyncResult.LOADING_IN_PROGRESS
            },
            phase = status.phase,
            progress = status.progress,
            processedMovies = status.processedMovies,
        )
    }
}
