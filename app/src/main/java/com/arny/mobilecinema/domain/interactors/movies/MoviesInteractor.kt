package com.arny.mobilecinema.domain.interactors.movies

import androidx.paging.PagingData
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SimpleFloatRange
import com.arny.mobilecinema.domain.models.SimpleIntRange
import com.arny.mobilecinema.domain.models.ViewMovie
import kotlinx.coroutines.flow.Flow

interface MoviesInteractor {
    fun getMovies(
        search: String = "",
        order: String = "",
        searchType: String = "",
        searchAddTypes: List<String> = emptyList(),
        genres: List<String> = emptyList(),
        countries: List<String> = emptyList(),
        years: SimpleIntRange? = null,
        imdbs: SimpleFloatRange? = null,
        kps: SimpleFloatRange? = null,
        likesPriority: Boolean = true,
    ): Flow<PagingData<ViewMovie>>

    fun isPipModeEnable(): Boolean
    /** Возвращает фильм по строковому id, который хранится в поле pageUrl */
    fun getMovieByPageUrl(pageUrl: String): Flow<DataResult<Movie>>
    fun getMovie(id: Long): Flow<DataResult<Movie>>
    suspend fun saveOrder(order: String)
    suspend fun saveHistoryOrder(order: String)
    suspend fun saveFavoriteOrder(order: String)
    suspend fun getOrder(isHistory: Boolean): String
    fun isMoviesEmpty(): Flow<DataResult<Boolean>>
    fun getBaseUrl(): String
    suspend fun loadDistinctGenres(): List<String>
    suspend fun getMinMaxYears(): SimpleIntRange
    suspend fun getCountries(): List<String>
    fun isAvailableToDownload(selectedCinemaUrl: String?, type: MovieType): Boolean
    fun getFavoriteMovies(
        search: String,
        order: String,
        searchType: String
    ): Flow<PagingData<ViewMovie>>

    fun isFavoriteEmpty(): Flow<DataResult<Boolean>>
    fun isFavorite(movieId: Long): Flow<DataResult<Boolean>>
    fun clearAllFavorites(): Flow<DataResult<Unit>>
    fun onFavoriteToggle(movieId: Long): Flow<DataResult<Boolean>>
}