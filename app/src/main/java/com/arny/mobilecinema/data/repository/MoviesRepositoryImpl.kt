package com.arny.mobilecinema.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.db.daos.HistoryDao
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.db.models.HistoryEntity
import com.arny.mobilecinema.data.db.sources.HistoryPagingSource
import com.arny.mobilecinema.data.db.sources.MainPagingSource
import com.arny.mobilecinema.data.models.MovieMapper
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.repository.prefs.PrefsConstants
import com.arny.mobilecinema.data.repository.resources.AppResourcesProvider
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.SimpleFloatRange
import com.arny.mobilecinema.domain.models.SimpleIntRange
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.domain.repository.MoviesRepository
import javax.inject.Inject

class MoviesRepositoryImpl @Inject constructor(
    private val movieMapper: MovieMapper,
    private val movieDao: MovieDao,
    private val historyDao: HistoryDao,
    private val prefs: Prefs,
    private val appResources: AppResourcesProvider,
) : MoviesRepository {

    override var orderPref: String
        get() = prefs.get<String>(PrefsConstants.ORDER).orEmpty()
        set(value) {
            prefs.put(PrefsConstants.ORDER, value)
        }

    override var historyOrderPref: String
        get() = prefs.get<String>(PrefsConstants.HISTORY_ORDER).orEmpty()
        set(value) {
            prefs.put(PrefsConstants.HISTORY_ORDER, value)
        }

    override var pipModePref: Boolean
        get() = prefs.get<Boolean>(PrefsConstants.PREF_KEY_PIP_MODE) ?: false
        set(value) {
            prefs.put(PrefsConstants.PREF_KEY_PIP_MODE, value)
        }

    override fun getMovies(
        search: String,
        order: String,
        searchType: String,
        searchAddTypes: List<String>,
        genres: List<String>,
        countries: List<String>,
        years: SimpleIntRange?,
        imdbs: SimpleFloatRange?,
        kps: SimpleFloatRange?,
        likesPriority: Boolean,
    ): Pager<Int, ViewMovie> = Pager(
        PagingConfig(
            pageSize = 20,
            enablePlaceholders = false,
            initialLoadSize = 20
        ),
    ) {
        MainPagingSource(
            dao = movieDao,
            search = search.trim(),
            order = order,
            searchType = searchType,
            genres = genres,
            countries = countries,
            years = years,
            imdbs = imdbs,
            kps = kps,
            searchAddTypes = searchAddTypes,
            likesPriority = likesPriority
        )
    }

    override suspend fun isMoviesEmpty(): Boolean = movieDao.getCount() == 0

    override fun getHistoryMovies(
        search: String,
        order: String,
        searchType: String
    ): Pager<Int, ViewMovie> =
        Pager(
            PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                initialLoadSize = 20
            ),
        ) { HistoryPagingSource(historyDao, search, order, searchType) }

    override fun getMovie(id: Long): Movie? =
        movieDao.getMovie(id)?.let { movieMapper.transform(it) }

    override fun getMovie(pageUrl: String): Movie? =
        movieDao.getMovie(pageUrl)?.let { movieMapper.transform(it) }

    override fun getGenres(): List<String> = appResources.getStringArray(R.array.genres)

    override fun getMinMaxYears(): SimpleIntRange = movieDao.getYearsMinMax()

    override fun getCountries(): List<String> = appResources.getStringArray(R.array.countries)

    override fun getSaveData(movieDbId: Long?): HistoryEntity? = historyDao.getHistory(movieDbId)

    override fun insertCinemaPosition(
        movieDbId: Long,
        position: Long,
        currentTimeMillis: Long
    ): Boolean =
        historyDao.insert(
            HistoryEntity(
                movieDbId = movieDbId,
                position = position,
                latestTime = currentTimeMillis
            )
        ) > 0L

    override fun updateCinemaPosition(
        movieDbId: Long?,
        position: Long,
        currentTimeMillis: Long
    ): Boolean {
        return historyDao.updateHistory(
            movieDbId = movieDbId,
            position = position,
            currentTimeMs = currentTimeMillis
        ) != 0
    }

    override fun insertSerialPosition(
        movieDbId: Long,
        season: Int,
        episode: Int,
        episodePosition: Long,
        currentTimeMs: Long
    ): Boolean = historyDao.insert(
        HistoryEntity(
            movieDbId = movieDbId,
            position = episodePosition,
            episode = episode,
            season = season,
            latestTime = currentTimeMs
        )
    ) > 0L

    override fun updateSerialPosition(
        movieDbId: Long?,
        season: Int,
        episode: Int,
        time: Long,
        currentTimeMs: Long
    ): Boolean = historyDao.updateHistory(
        movieDbId = movieDbId,
        season = season,
        episode = episode,
        position = time,
        currentTimeMs = currentTimeMs
    ) != 0

    override suspend fun isHistoryEmpty(): Boolean {
        return historyDao.getHistoryCount() == 0
    }

    override fun clearViewHistory(movieDbId: Long?): Boolean =
        historyDao.deleteHistory(movieDbId) > 0

    override fun clearAllViewHistory(): Boolean = historyDao.deleteAllHistory() > 0

    override fun saveOrder(order: String) {
        this.orderPref = order
    }

    override fun saveHistoryOrder(order: String) {
        this.historyOrderPref = order
    }
}