package com.arny.mobilecinema.domain.interactors

import androidx.paging.PagingData
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.models.DataThrowable
import com.arny.mobilecinema.data.models.doAsync
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.SaveData
import com.arny.mobilecinema.domain.models.SimpleFloatRange
import com.arny.mobilecinema.domain.models.SimpleIntRange
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.domain.repository.MoviesRepository
import com.arny.mobilecinema.domain.repository.UpdateRepository
import com.arny.mobilecinema.presentation.player.PlayerSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MoviesInteractorImpl @Inject constructor(
    private val repository: MoviesRepository,
    private val updateRepository: UpdateRepository,
    private val playerSource: PlayerSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MoviesInteractor {

    override fun isPipModeEnable(): Boolean = repository.prefPipMode

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
    ): Flow<PagingData<ViewMovie>> {
        val type = searchType.ifBlank { AppConstants.SearchType.TITLE }
        return repository.getMovies(
            search = search,
            order = order,
            searchType = type,
            searchAddTypes = searchAddTypes,
            genres = genres,
            countries = countries,
            years = years,
            imdbs = imdbs
        ).flow
    }

    override suspend fun loadDistinctGenres(): List<String> = withContext(dispatcher) {
        repository.getGenres()
    }

    override suspend fun getMinMaxYears(): SimpleIntRange = withContext(dispatcher) {
        repository.getMinMaxYears()
    }

    override suspend fun getCountries(): List<String> = withContext(dispatcher) {
        repository.getCountries()
    }

    override fun getBaseUrl(): String = updateRepository.baseUrl

    override fun addToHistory(dbId: Long?): Flow<DataResult<Boolean>> = doAsync {
        var result = false
        if (repository.prefHistoryOnCache) {
            val data = repository.getSaveData(dbId)
            val position = data.position
            result = position == 0L
            repository.saveCinemaPosition(dbId, position)
        }
        result
    }

    override fun getHistoryMovies(
        search: String,
        order: String,
        searchType: String
    ): Flow<PagingData<ViewMovie>> {
        val type = searchType.ifBlank { AppConstants.SearchType.TITLE }
        return repository.getHistoryMovies(search, order, type).flow
    }

    override fun isHistoryEmpty(): Flow<DataResult<Boolean>> = doAsync {
        repository.isHistoryEmpty()
    }

    override fun isMoviesEmpty(): Flow<DataResult<Boolean>> = doAsync {
        repository.isMoviesEmpty()
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

    override fun clearViewHistory(dbId: Long?): Flow<DataResult<Boolean>> = doAsync {
        repository.clearViewHistory(dbId)
    }

    override fun clearAllViewHistory(): Flow<DataResult<Boolean>> = doAsync {
        playerSource.clearAllDownloaded()
        repository.clearAllViewHistory()
    }

    override suspend fun saveOrder(order: String) {
        withContext(Dispatchers.IO) {
            repository.saveOrder(order)
        }
    }

    override suspend fun getOrder(): String = withContext(Dispatchers.IO) {
        var order = repository.order
        if (order.isBlank()) {
            order = AppConstants.Order.NONE
        }
        order
    }
}
