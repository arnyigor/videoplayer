package com.arny.mobilecinema.presentation.settings

import com.arny.mobilecinema.domain.interactors.update.CatalogUpdatePhase
import com.arny.mobilecinema.domain.interactors.update.CatalogUpdateStatus
import com.arny.mobilecinema.domain.interactors.update.CatalogUpdateTracker
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CatalogUpdateRecoveryTest {
    @Test fun `unfinished persisted operation is interrupted and can be restarted`() {
        for (phase in CatalogUpdatePhase.entries.filter { CatalogUpdateStatus(it).isRunning }) {
            val persisted = mutableListOf<CatalogUpdateStatus>()
            val tracker = CatalogUpdateTracker(CatalogUpdateStatus(phase, 42)) { persisted += it }
            assertEquals(CatalogUpdatePhase.INTERRUPTED, tracker.status.value.phase)
            assertFalse(tracker.status.value.isRunning)
            assertEquals(CatalogUpdatePhase.INTERRUPTED, persisted.single().phase)
            assertTrue(tracker.tryQueue())
            assertEquals(CatalogUpdatePhase.QUEUED, persisted.last().phase)
            assertFalse(tracker.tryQueue())
        }
    }

    @Test fun `terminal result survives tracker recreation`() {
        var stored = CatalogUpdateStatus()
        val tracker = CatalogUpdateTracker(stored) { stored = it }
        tracker.tryQueue()
        tracker.update(CatalogUpdatePhase.SUCCEEDED, processedMovies = 57)
        val restored = CatalogUpdateTracker(stored)
        assertEquals(CatalogUpdatePhase.SUCCEEDED, restored.status.value.phase)
        assertEquals(57, restored.status.value.processedMovies)
        assertFalse(restored.status.value.isRunning)
    }
}
