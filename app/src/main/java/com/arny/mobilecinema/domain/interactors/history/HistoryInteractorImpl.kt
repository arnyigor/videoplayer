package com.arny.mobilecinema.domain.interactors.history

import androidx.paging.PagingData
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.models.DataThrowable
import com.arny.mobilecinema.data.models.doAsync
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.models.SaveData
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.domain.repository.MoviesRepository
import com.arny.mobilecinema.presentation.player.PlayerSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class HistoryInteractorImpl @Inject constructor(
    private val repository: MoviesRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val playerSource: PlayerSource,
) : HistoryInteractor {
    override fun addToHistory(movieDbId: Long): Flow<DataResult<Boolean>> = doAsync {
        getResultAddToHistory(
            movieDbId = movieDbId,
            save = repository.prefHistoryOnCache
        )
    }

    override fun isHistoryEmpty(): Flow<DataResult<Boolean>> = doAsync {
        repository.isHistoryEmpty()
    }

    override fun getSaveData(movieDbId: Long?): Flow<DataResult<SaveData>> = doAsync {
        getHistoryData(movieDbId)
    }

    override suspend fun saveCinemaPosition(
        movieDbId: Long,
        position: Long
    ): Boolean = withContext(dispatcher) {
        getResultAddToHistory(
            movieDbId = movieDbId,
            position = position,
            save = true
        )
    }

    override suspend fun saveSerialPosition(
        movieDbId: Long,
        season: Int,
        episode: Int,
        episodePosition: Long
    ): Boolean = withContext(dispatcher) {
        val data = getHistoryData(movieDbId)
        if (data.movieDbId != null) {
            repository.updateSerialPosition(data.movieDbId, season, episode, episodePosition)
        } else {
            repository.insertSerialPosition(movieDbId, season, episode, episodePosition)
        }
    }

    override fun clearViewHistory(movieDbId: Long?): Flow<DataResult<Boolean>> =
        doAsync {
            val clearViewHistory = repository.clearViewHistory(movieDbId)
            if (!clearViewHistory) {
                throw DataThrowable(R.string.error_history_remove_movie_fail)
            }
            true
        }

    override fun clearAllViewHistory(): Flow<DataResult<Boolean>> = doAsync {
        playerSource.clearAllDownloaded()
        repository.clearAllViewHistory()
    }

    override fun getHistoryMovies(
        search: String,
        order: String,
        searchType: String
    ): Flow<PagingData<ViewMovie>> {
        val type = searchType.ifBlank { AppConstants.SearchType.TITLE }
        return repository.getHistoryMovies(search, order, type).flow
    }

    private fun getResultAddToHistory(
        movieDbId: Long,
        position: Long = 0,
        save: Boolean
    ): Boolean = if (save) {
        val data = getHistoryData(movieDbId)
        if (data.movieDbId != null) {
            repository.updateCinemaPosition(data.movieDbId, position)
        } else {
            repository.insertCinemaPosition(movieDbId, position)
        }
    } else {
        false
    }

    private fun getHistoryData(movieDbId: Long?): SaveData {
        val data = repository.getSaveData(movieDbId)
        return if (data != null && data.movieDbId != 0L) {
            SaveData(
                movieDbId = data.movieDbId,
                position = data.position,
                season = data.season,
                episode = data.episode
            )
        } else {
            SaveData()
        }
    }

}