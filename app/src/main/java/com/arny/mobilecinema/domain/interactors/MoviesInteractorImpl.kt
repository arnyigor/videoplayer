package com.arny.mobilecinema.domain.interactors

import androidx.paging.PagingData
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.models.DataThrowable
import com.arny.mobilecinema.data.models.doAsync
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.SaveData
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.domain.repository.MoviesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MoviesInteractorImpl @Inject constructor(
    private val repository: MoviesRepository,
) : MoviesInteractor {
    override fun getMovies(search: String, order: String): Flow<PagingData<ViewMovie>> =
        repository.getMovies(search, order).flow

    override fun getHistoryMovies(search: String): Flow<PagingData<ViewMovie>> =
        repository.getHistoryMovies(search).flow

    override fun isHistoryEmpty(): Flow<DataResult<Boolean>> = doAsync {
        repository.isHistoryEmpty()
    }

    override fun getMovie(id: Long): Flow<DataResult<Movie>> = doAsync {
        repository.getMovie(id) ?: throw DataThrowable(R.string.movie_not_found)
    }

    override fun getSaveData(dbId: Long?): Flow<DataResult<SaveData>> = doAsync {
        repository.getSaveData(dbId)
    }

    override suspend fun saveCinemaPosition(id: Long?, position: Long) {
        withContext(Dispatchers.IO) {
            repository.saveCinemaPosition(id, position)
        }
    }

    override suspend fun saveSerialPosition(
        id: Long?,
        season: Int,
        episode: Int,
        episodePosition: Long
    ) {
        withContext(Dispatchers.IO) {
            repository.saveSerialPosition(id, season, episode, episodePosition)
        }
    }

    override fun clearCache(dbId: Long?): Flow<DataResult<Boolean>> = doAsync {
        repository.clearCache(dbId)
    }
}
