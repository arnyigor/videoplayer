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
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MoviesInteractorImpl @Inject constructor(
    private val repository: MoviesRepository,
) : MoviesInteractor {
    override fun getMovies(search: String): Flow<PagingData<ViewMovie>> =
        repository.getMovies(search).flow

    override fun getMovie(id: Long): Flow<DataResult<Movie>> = doAsync {
        repository.getMovie(id) ?: throw DataThrowable(R.string.movie_not_found)
    }

    override fun getSaveData(dbId: Long?): SaveData = repository.getSaveData(dbId)

    override fun saveMoviePosition(dbId: Long?, position: Long) {
        repository.saveMoviePosition(dbId,position)
    }

    override fun saveSerialPosition(dbId: Long?, season: Int, episode: Int) {
        repository.saveSerialPosition(dbId, season, episode)
    }
}
