package com.arny.mobilecinema.domain.interactors.history

import androidx.paging.PagingData
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.models.DataThrowable
import com.arny.mobilecinema.data.models.doAsync
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SaveData
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.domain.repository.MoviesRepository
import com.arny.mobilecinema.presentation.player.PlayerSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class HistoryInteractorImpl @Inject constructor(
    private val repository: MoviesRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val playerSource: PlayerSource,
) : HistoryInteractor {
    private val _serialPositionChange = MutableStateFlow(false)
    override val serialPositionChange: Flow<Boolean>
        get() = _serialPositionChange.asStateFlow()

    override fun setSerialPositionChange(change: Boolean) {
        _serialPositionChange.value = false
    }

    override fun addToViewHistory(movieDbId: Long): Flow<DataResult<Boolean>> = doAsync {
        getResultAddToViewHistory(
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
        time: Long
    ): Boolean = withContext(dispatcher) {
        getResultAddToViewHistory(
            movieDbId = movieDbId,
            position = time,
            save = true
        )
    }

    override suspend fun saveSerialPosition(
        movieDbId: Long,
        season: Int,
        episode: Int,
        time: Long
    ): Boolean = withContext(dispatcher) {
        val data = getHistoryData(movieDbId)
        _serialPositionChange.value =
            data.seasonPosition != season || data.episodePosition != episode
        if (data.movieDbId != null) {
            repository.updateSerialPosition(data.movieDbId, season, episode, time)
        } else {
            repository.insertSerialPosition(movieDbId, season, episode, time)
        }
    }

    override fun clearViewHistory(
        movieDbId: Long?,
        type: MovieType?,
        total: Boolean,
        url: String
    ): Flow<DataResult<Boolean>> =
        doAsync {
            if (total) {
                val clearViewHistory = repository.clearViewHistory(movieDbId)
                if (!clearViewHistory && type == MovieType.CINEMA) {
                    throw DataThrowable(R.string.error_history_remove_movie_fail)
                }
            }
            playerSource.clearDownloaded(url)
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

    private fun getResultAddToViewHistory(
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
                time = data.position,
                seasonPosition = data.season,
                episodePosition = data.episode
            )
        } else {
            SaveData()
        }
    }

}