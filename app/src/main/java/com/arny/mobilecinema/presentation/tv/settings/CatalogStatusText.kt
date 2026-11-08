package com.arny.mobilecinema.presentation.tv.settings

import android.content.Context
import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.interactors.update.CatalogUpdatePhase

internal fun catalogStatusText(context: Context, phase: CatalogUpdatePhase, progress: Int?, count: Int? = null): String {
    val text = when (phase) {
        CatalogUpdatePhase.IDLE -> context.getString(R.string.pref_sync_status_never)
        CatalogUpdatePhase.QUEUED -> context.getString(R.string.pref_sync_status_started)
        CatalogUpdatePhase.DOWNLOADING -> context.getString(R.string.pref_sync_downloading)
        CatalogUpdatePhase.UNPACKING -> context.getString(R.string.pref_sync_unpacking)
        CatalogUpdatePhase.IMPORTING -> context.getString(R.string.pref_sync_importing)
        CatalogUpdatePhase.SCRAPING -> context.getString(R.string.pref_sync_scraping)
        CatalogUpdatePhase.SUCCEEDED -> count?.let { context.getString(R.string.pref_sync_success_count, it) }
            ?: context.getString(R.string.pref_sync_success)
        CatalogUpdatePhase.FAILED -> context.getString(R.string.pref_sync_status_error)
        CatalogUpdatePhase.INTERRUPTED -> context.getString(R.string.catalog_update_interrupted)
        CatalogUpdatePhase.CANCELLED -> context.getString(R.string.pref_sync_cancelled)
    }
    return if (progress != null && phase in setOf(CatalogUpdatePhase.DOWNLOADING, CatalogUpdatePhase.IMPORTING, CatalogUpdatePhase.SCRAPING))
        context.getString(R.string.pref_sync_progress, text, progress) else text
}
