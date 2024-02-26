package com.arny.mobilecinema.domain.interactors.update

import android.content.Context
import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.models.doAsync
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.models.DataUpdateResult
import com.arny.mobilecinema.domain.repository.UpdateRepository
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject

class DataUpdateInteractorImpl @Inject constructor(
    private val context: Context,
    private val repository: UpdateRepository
) : DataUpdateInteractor {
    private var forceUpdate = false

    override suspend fun checkBaseUrl(): Flow<DataResult<Boolean>> = doAsync {
        repository.checkBaseUrl()
    }

    override suspend fun requestFile(force: Boolean, hasPartUpdate: Boolean) {
        this.forceUpdate = force
        val dataLink = when {
            hasPartUpdate && BuildConfig.DEBUG -> BuildConfig.DATA_0_DEBUG_LINK
            !hasPartUpdate && BuildConfig.DEBUG -> BuildConfig.DATA_DEBUG_LINK
            hasPartUpdate -> BuildConfig.DATA_0_LINK
            !hasPartUpdate -> BuildConfig.DATA_LINK
            else -> BuildConfig.DATA_LINK
        }
        repository.downloadUpdates(dataLink, forceUpdate)
    }

    override fun resetUpdate() {
        repository.newUpdate = ""
        repository.checkUpdate = false
    }

    override suspend fun getUpdateDate(force: Boolean): Flow<DataResult<DataUpdateResult>> =
        doAsync {
            val hasMovies = repository.hasMovies()
            if (!hasMovies) {
                repository.lastUpdate = ""
            }
            if (force || !hasMovies) {
                resetUpdate()
            }
            var newUpdate = ""
            var hasPartUpdateFile = false
            if (!repository.checkUpdate && repository.newUpdate.isBlank()) {
                repository.checkUpdate = true
                val updateFile = repository.downloadFile(
                    if (BuildConfig.DEBUG) BuildConfig.UPDATE_DEBUG_LINK else BuildConfig.UPDATE_LINK,
                    AppConstants.UPDATE_FILE
                )
                newUpdate = updateFile.readText()
                updateFile.delete()
                if (hasMovies && repository.hasLastUpdates()) {
                    hasPartUpdateFile = repository.checkPath(
                        if (BuildConfig.DEBUG) BuildConfig.DATA_0_DEBUG_LINK else BuildConfig.DATA_0_LINK,
                    )
                }
            }
            if (repository.lastUpdate != newUpdate && newUpdate.isNotBlank()) {
                repository.newUpdate = newUpdate
                DataUpdateResult(newUpdate, hasPartUpdateFile)
            } else {
                DataUpdateResult("")
            }
        }
}
