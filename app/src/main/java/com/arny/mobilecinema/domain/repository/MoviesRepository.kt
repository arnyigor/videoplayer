package com.arny.mobilecinema.domain.repository

import androidx.paging.Pager
import com.arny.mobilecinema.data.db.models.HistoryEntity
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.OrderKey
import com.arny.mobilecinema.domain.models.SimpleFloatRange
import com.arny.mobilecinema.domain.models.SimpleIntRange
import com.arny.mobilecinema.domain.models.ViewMovie

interface MoviesRepository {
    var orderPref: String
    var historyOrderPref: String
    var pipModePref: Boolean
    fun getMovies(
        search: String,
        order: String,
        searchType: String = "",
        searchAddTypes: List<String> = emptyList(),
        genres: List<String> = emptyList(),
        countries: List<String> = emptyList(),
        years: SimpleIntRange? = null,
        imdbs: SimpleFloatRange? = null,
        kps: SimpleFloatRange? = null,
        likesPriority: Boolean,
    ): Pager<Int, ViewMovie>

    /**
     * Одна страница каталога «Фильмы»/«Сериалы» (CAT-01).
     * Пагинация выполняется экраном: пейджинг-TV использует свой LazyGrid, а не Paging3.
     */
    suspend fun getCatalogMoviesPage(
        movieType: MovieType,
        order: String,
        genres: List<String>,
        limit: Int,
        offset: Int,
    ): List<ViewMovie>

    suspend fun getRecentMovies(
        updatedFrom: Long,
        movieTypes: List<MovieType>,
        limit: Int
    ): List<ViewMovie>

    /**
     * Поиск по локальному каталогу для единого экрана поиска (SRCH-02/SRCH-03).
     *
     * Отдаёт страницу, а не Pager: экран смешивает локальную и внешнюю выдачу
     * в один список, и Paging3 здесь не нужен.
     */
    suspend fun searchLocal(
        query: String,
        kinds: List<MovieType>,
        genre: String? = null,
        country: String? = null,
        years: SimpleIntRange? = null,
        order: String = AppConstants.Order.SMART,
        limit: Int = 40,
        offset: Int = 0,
    ): List<ViewMovie>

    suspend fun getBestNowMovies(
        fromYear: Int,
        toYear: Int,
        movieTypes: List<MovieType>,
        limit: Int
    ): List<ViewMovie>

    suspend fun getForYouMovies(
        movieTypes: List<MovieType>,
        excludedIds: Set<Long>,
        limit: Int
    ): List<ViewMovie>
    fun getMovie(id: Long): Movie?
    fun getMovie(pageUrl: String): Movie?
    fun getSaveData(movieDbId: Long?): HistoryEntity?
    fun insertCinemaPosition(movieDbId: Long, position: Long, currentTimeMillis: Long): Boolean
    fun updateCinemaPosition(movieDbId: Long?, position: Long, currentTimeMillis: Long): Boolean

    fun insertSerialPosition(
        movieDbId: Long,
        season: Int,
        episode: Int,
        episodePosition: Long,
        currentTimeMs: Long
    ): Boolean

    fun updateSerialPosition(
        movieDbId: Long?,
        season: Int,
        episode: Int,
        time: Long,
        currentTimeMs: Long
    ): Boolean

    fun getHistoryMovies(search: String, order: String, searchType: String): Pager<Int, ViewMovie>
    fun clearViewHistory(movieDbId: Long?): Boolean
    suspend fun isHistoryEmpty(): Boolean
    fun saveOrder(order: String)
    fun saveHistoryOrder(order: String)
    fun clearAllViewHistory(): Boolean
    suspend fun isMoviesEmpty(): Boolean
    fun getGenres(): List<String>
    /** Только канонические жанры для чипов каталога (без мусорных строк из БД). */
    fun getCanonicalGenres(): List<String>
    fun getMinMaxYears(): SimpleIntRange
    fun getCountries(): List<String>
    fun getFavoriteMoviesPager(
        search: String,
        order: String,
        searchType: String
    ): Pager<Int, ViewMovie>

    fun getOrder(key: OrderKey): String
    fun setOrder(key: OrderKey, value: String)
    suspend fun isFavoriteEmpty(): Boolean
    fun clearAllFavorites()
    fun toggleFavorite(movieId: Long): Boolean
    fun isFavorite(movieId: Long): Boolean
}
