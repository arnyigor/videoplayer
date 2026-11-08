package com.arny.mobilecinema.domain.interactors.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CatalogUpdatePhase {
    IDLE, QUEUED, DOWNLOADING, UNPACKING, IMPORTING, SCRAPING, SUCCEEDED, FAILED, CANCELLED, INTERRUPTED,
}

data class CatalogUpdateStatus(
    val phase: CatalogUpdatePhase = CatalogUpdatePhase.IDLE,
    val progress: Int? = null,
    /** Number of input records processed, not a count of newly added titles. */
    val processedMovies: Int? = null,
) {
    val isRunning: Boolean get() = phase in setOf(
        CatalogUpdatePhase.QUEUED, CatalogUpdatePhase.DOWNLOADING, CatalogUpdatePhase.UNPACKING,
        CatalogUpdatePhase.IMPORTING, CatalogUpdatePhase.SCRAPING,
    )
}

/** Process-scoped status shared by the executor and screens, including newly opened settings. */
class CatalogUpdateTracker(
    initialStatus: CatalogUpdateStatus = CatalogUpdateStatus(),
    private val persist: (CatalogUpdateStatus) -> Unit = {},
) {
    private val _status = MutableStateFlow(if (initialStatus.isRunning)
        initialStatus.copy(phase = CatalogUpdatePhase.INTERRUPTED) else initialStatus)

    init { if (initialStatus.isRunning) persist(_status.value) }
    val status = _status.asStateFlow()

    @Synchronized
    fun tryQueue(): Boolean {
        if (_status.value.isRunning) return false
        update(CatalogUpdatePhase.QUEUED)
        return true
    }

    @Synchronized
    fun update(phase: CatalogUpdatePhase, progress: Int? = null, processedMovies: Int? = null) {
        val status = CatalogUpdateStatus(phase, progress?.takeIf { it >= 0 }?.coerceAtMost(100), processedMovies)
        persist(status)
        _status.value = status
    }
}
