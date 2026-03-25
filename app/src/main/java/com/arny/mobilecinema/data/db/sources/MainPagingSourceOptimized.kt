package com.arny.mobilecinema.data.db.sources

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.sqlite.db.SimpleSQLiteQuery
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SimpleFloatRange
import com.arny.mobilecinema.domain.models.SimpleIntRange
import com.arny.mobilecinema.domain.models.ViewMovie
import timber.log.Timber

/**
 * Оптимизированный PagingSource для загрузки списка видео.
 * Улучшения:
 * 1. Кэширование часто используемых запросов
 * 2. Оптимизированное управление ключами страниц
 * 3. Логирование для отладки производительности
 * 4. Улучшенная обработка ошибок
 */
class MainPagingSourceOptimized(
    private val dao: MovieDao,
    private val search: String,
    private val order: String,
    private val searchType: String,
    private val searchAddTypes: List<String>,
    private val genres: List<String>,
    private val countries: List<String>,
    private val years: SimpleIntRange?,
    private val imdbs: SimpleFloatRange?,
    private val kps: SimpleFloatRange?,
    private val likesPriority: Boolean
) : PagingSource<Int, ViewMovie>() {

    companion object {
        private const val TAG = "MainPagingSource"
        private const val MAX_RETRY_ATTEMPTS = 3
    }

    // Кэш для часто используемых результатов (например, при быстрой прокрутке вперед/назад)
    private val pageCache = mutableMapOf<Int, List<ViewMovie>>()
    private var retryCount = 0

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ViewMovie> {
        val page = params.key ?: 0
        
        // Проверяем кэш сначала
        pageCache[page]?.let { cachedData ->
            Timber.d("Returning cached data for page $page (size: ${cachedData.size})")
            return LoadResult.Page(
                data = cachedData,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (cachedData.isEmpty()) null else page + 1
            )
        }

        return try {
            Timber.d("Loading page $page with loadSize: ${params.loadSize}")
            
            val list = dao.getMovies(
                getMoviesSQL(
                    search = search,
                    order = order,
                    searchType = searchType,
                    movieTypes = getMovieTypes(),
                    genres = genres,
                    countries = countries,
                    years = years,
                    imdbs = imdbs,
                    kps = kps,
                    likesPriority = likesPriority,
                    limit = params.loadSize,
                    offset = page * params.loadSize,
                )
            )

            Timber.d("Page $page loaded successfully: ${list.size} items")
            
            // Сохраняем в кэш
            pageCache[page] = list

            // Ограничиваем размер кэша (например, 5 страниц)
            if (pageCache.size > 5) {
                val oldestKey = pageCache.keys.minOrNull()
                oldestKey?.let { pageCache.remove(it) }
            }

            LoadResult.Page(
                data = list,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (list.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            Timber.e(e, "Error loading page $page")
            
            // Повторная попытка для временных ошибок
            if (retryCount < MAX_RETRY_ATTEMPTS) {
                retryCount++
                Timber.w("Retrying load for page $page (attempt $retryCount)")
                return load(params)
            }
            
            retryCount = 0
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, ViewMovie>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            // При рефреше пытаемся сохранить примерно ту же позицию
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }

    init {
        // PagingSource.invalidate() cannot be overridden.
        // We can hook into invalidation by listening to the PagingSource's internal state
        // or by managing the cache externally.
        // As a workaround here, we can simply rely on the fact that when this instance
        // is discarded, its resources are GC'd.
    }

    private fun getMovieTypes() = searchAddTypes.map {
        when (it) {
            AppConstants.SearchType.CINEMA -> MovieType.CINEMA
            AppConstants.SearchType.SERIAL -> MovieType.SERIAL
            else -> MovieType.NO_TYPE
        }
    }

    private fun getMoviesSQL(
        search: String,
        order: String,
        searchType: String,
        movieTypes: List<MovieType>,
        genres: List<String>,
        countries: List<String>,
        years: SimpleIntRange?,
        imdbs: SimpleFloatRange?,
        kps: SimpleFloatRange?,
        likesPriority: Boolean,
        limit: Int,
        offset: Int,
    ): SimpleSQLiteQuery {
        return PagingSourceHelperOptimized.getMoviesSQL(
            search = search,
            order = order,
            searchType = searchType,
            movieTypes = movieTypes,
            genres = genres,
            countries = countries,
            years = years,
            imdbs = imdbs,
            kps = kps,
            likesPriority = likesPriority,
            limit = limit,
            offset = offset
        )
    }
}