package com.arny.mobilecinema.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.arny.mobilecinema.data.db.daos.HistoryDao
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.db.models.HistoryEntity
import com.arny.mobilecinema.data.db.sources.HistoryPagingSource
import com.arny.mobilecinema.data.db.sources.MainPagingSource
import com.arny.mobilecinema.data.models.MovieMapper
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.repository.prefs.PrefsConstants
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.SaveData
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.domain.repository.MoviesRepository
import javax.inject.Inject

class MoviesRepositoryImpl @Inject constructor(
    private val movieMapper: MovieMapper,
    private val movieDao: MovieDao,
    private val historyDao: HistoryDao,
    private val prefs: Prefs
) : MoviesRepository {
    override var order: String
        get() = prefs.get<String>(PrefsConstants.ORDER).orEmpty()
        set(value) {
            prefs.put(PrefsConstants.ORDER, value)
        }

    override fun getMovies(
        search: String,
        order: String,
        searchType: String
    ): Pager<Int, ViewMovie> = Pager(
        PagingConfig(
            pageSize = 20,
            enablePlaceholders = false,
            initialLoadSize = 20
        ),
    ) { MainPagingSource(movieDao, search.trim(), order, searchType) }

    override suspend fun isHistoryEmpty(): Boolean = historyDao.getHistoryIds().isEmpty()

    override fun getHistoryMovies(search: String): Pager<Int, ViewMovie> =
        Pager(
            PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                initialLoadSize = 20
            ),
        ) { HistoryPagingSource(historyDao, search) }

    override fun getMovie(id: Long): Movie? {
        val movie = movieDao.getMovie(id)?.let { movieMapper.transform(it) }
        return movie
    }

    override fun getSaveData(dbId: Long?): SaveData {
        val history = historyDao.getHistory(dbId)
        return if (history != null) {
            SaveData(
                dbId = history.movieDbId,
                position = history.position,
                season = history.season,
                episode = history.episode
            )
        } else {
            SaveData()
        }
    }

    override fun saveCinemaPosition(dbId: Long?, position: Long) {
        if (dbId != null) {
            val history = historyDao.getHistory(dbId)
            if (history == null) {
                historyDao.insert(
                    HistoryEntity(
                        movieDbId = dbId,
                        position = position
                    )
                )
            } else {
                historyDao.updateHistory(
                    movieDbId = dbId,
                    position = position
                )
            }
        }
    }

    override fun saveSerialPosition(id: Long?, season: Int, episode: Int, episodePosition: Long) {
        if (id != null) {
            val history = historyDao.getHistory(id)
            if (history == null) {
                historyDao.insert(
                    HistoryEntity(
                        movieDbId = id,
                        season = season,
                        episode = episode,
                        position = episodePosition
                    )
                )
            } else {
                historyDao.updateHistory(
                    movieDbId = id,
                    season = season,
                    episode = episode,
                    position = episodePosition
                )
            }
        }
    }

    override fun clearViewHistory(dbId: Long?): Boolean = historyDao.deleteHistory(dbId) > 0

    override fun clearAllViewHistory(): Boolean = historyDao.deleteAllHistory() > 0

    override fun saveOrder(order: String) {
        this.order = order
    }
}