package com.arny.mobilecinema.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.db.sources.MainPagingSource
import com.arny.mobilecinema.data.models.MovieMapper
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.SaveData
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.domain.repository.MoviesRepository
import javax.inject.Inject

class MoviesRepositoryImpl @Inject constructor(
    private val movieMapper: MovieMapper,
    private val movieDao: MovieDao
) : MoviesRepository {
    private val history = mutableListOf<SaveData>()
    override fun getMovies(search: String): Pager<Int, ViewMovie> {
        return Pager(
            PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                initialLoadSize = 20
            ),
        ) { MainPagingSource(movieDao, search.trim()) }
    }

    override fun getMovie(id: Long): Movie? =
        movieDao.getMovie(id)?.let { movieMapper.transform(it) }

    override fun getSaveData(dbId: Long?): SaveData = history.find { it.dbId == dbId } ?: SaveData()

    override fun saveMoviePosition(dbId: Long?, position: Long) {
        if (dbId != null) {
            val index = history.indexOfFirst { it.dbId == dbId }
            if (index == -1) {
                history.add(SaveData(dbId = dbId, position = position))
            } else {
                history[index] = history[index].copy(
                    position = position
                )
            }
        }
    }

    override fun saveSerialPosition(dbId: Long?, season: Int, episode: Int) {
        if (dbId != null) {
            val index = history.indexOfFirst { it.dbId == dbId }
            if (index == -1) {
                history.add(SaveData(dbId = dbId, season = season, episode = episode))
            } else {
                history[index] = history[index].copy(
                    season = season,
                    episode = episode
                )
            }
        }
    }
}