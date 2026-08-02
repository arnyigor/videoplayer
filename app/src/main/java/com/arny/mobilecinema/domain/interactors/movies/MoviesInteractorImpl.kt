package com.arny.mobilecinema.domain.interactors.movies

import androidx.paging.PagingData
import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.models.DataThrowable
import com.arny.mobilecinema.data.models.doAsync
import com.arny.mobilecinema.data.network.jsoup.JsoupService
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.interactors.jsoupupdate.getComments
import com.arny.mobilecinema.domain.interactors.jsoupupdate.getCommentsPagesCount
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieComment
import com.arny.mobilecinema.domain.models.MovieCommentsPage
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.OrderKey
import com.arny.mobilecinema.domain.models.SimpleFloatRange
import com.arny.mobilecinema.domain.models.SimpleIntRange
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.domain.repository.MoviesRepository
import com.arny.mobilecinema.domain.repository.UpdateRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MoviesInteractorImpl(
    private val repository: MoviesRepository,
    private val updateRepository: UpdateRepository,
    private val jsoupService: JsoupService,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MoviesInteractor {

    override fun isPipModeEnable(): Boolean = repository.pipModePref

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
    ): Flow<PagingData<ViewMovie>> = repository.getMovies(
        search = search,
        order = order,
        searchType = searchType.ifBlank { AppConstants.SearchType.TITLE },
        searchAddTypes = searchAddTypes,
        genres = genres,
        countries = countries,
        years = years,
        imdbs = imdbs,
        kps = kps,
        likesPriority = likesPriority,
    ).flow

    override fun isFavorite(movieId: Long): Flow<DataResult<Boolean>> = doAsync {
        repository.isFavorite(movieId)
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

    override fun getBaseUrl(): String {
        val entryPointBaseUrl = BuildConfig.BASE_LINK.trim().trimEnd('/')
        if (entryPointBaseUrl.startsWith("http://", ignoreCase = true) ||
            entryPointBaseUrl.startsWith("https://", ignoreCase = true)
        ) {
            return entryPointBaseUrl
        }
        return updateRepository.baseUrl.trim().trimEnd('/')
    }

    override fun isMoviesEmpty(): Flow<DataResult<Boolean>> = doAsync {
        repository.isMoviesEmpty()
    }

    override fun getMovie(id: Long): Flow<DataResult<Movie>> = doAsync {
        repository.getMovie(id) ?: throw DataThrowable(R.string.movie_not_found)
    }

    override fun getMovieByPageUrl(pageUrl: String): Flow<DataResult<Movie>> = doAsync {
        repository.getMovie(pageUrl) ?: throw DataThrowable(R.string.movie_not_found)
    }

    override fun getMovieComments(pageUrl: String, maxPages: Int): Flow<DataResult<List<MovieComment>>> =
        doAsync(dispatcher) {
            val firstPage = loadCommentsPage(pageUrl, 1)
            val pagesCount = firstPage.totalPages.coerceAtMost(maxPages.coerceAtLeast(1))
            buildList {
                addAll(firstPage.comments)
                for (page in 2..pagesCount) {
                    addAll(loadCommentsPage(pageUrl, page).comments)
                }
            }.distinctBy { it.id.ifBlank { "${it.author}_${it.dateText}_${it.text}" } }
        }

    override fun getMovieCommentsPage(pageUrl: String, page: Int): Flow<DataResult<MovieCommentsPage>> =
        doAsync(dispatcher) {
            loadCommentsPage(pageUrl, page)
        }

    private fun loadCommentsPage(pageUrl: String, page: Int): MovieCommentsPage {
        val commentsUrl = buildCommentsUrl(pageUrl)
        val targetPage = page.coerceAtLeast(1)
        val document = jsoupService.loadPage(
            if (targetPage == 1) commentsUrl else "$commentsUrl/$targetPage"
        )
        return MovieCommentsPage(
            comments = getComments(document.body()),
            currentPage = targetPage,
            totalPages = getCommentsPagesCount(document.body()).coerceAtLeast(targetPage),
            addCommentUrl = buildAddCommentUrl(pageUrl),
        )
    }

    private fun buildCommentsUrl(pageUrl: String): String {
        val rawPageUrl = pageUrl.trim()
        val normalizedPageUrl = if (rawPageUrl.startsWith("http://", ignoreCase = true) ||
            rawPageUrl.startsWith("https://", ignoreCase = true)
        ) {
            rawPageUrl.substringAfter("://").substringAfter("/")
        } else {
            rawPageUrl.trimStart('/')
        }
        val (type, movieId) = getMovieUrlParts(normalizedPageUrl, pageUrl)
        return "${getBaseUrl()}/$type/comm/$movieId"
    }

    private fun buildAddCommentUrl(pageUrl: String): String {
        val rawPageUrl = pageUrl.trim()
        val normalizedPageUrl = if (rawPageUrl.startsWith("http://", ignoreCase = true) ||
            rawPageUrl.startsWith("https://", ignoreCase = true)
        ) {
            rawPageUrl.substringAfter("://").substringAfter("/")
        } else {
            rawPageUrl.trimStart('/')
        }
        val (type, movieId) = getMovieUrlParts(normalizedPageUrl, pageUrl)
        return "${getBaseUrl()}/$type/comm/$movieId"
    }

    private fun getMovieUrlParts(normalizedPageUrl: String, sourcePageUrl: String): Pair<String, String> {
        val match = Regex("^(films|serials)/(\\d+)").find(normalizedPageUrl)
            ?: throw IllegalArgumentException("Unsupported movie url: $sourcePageUrl")
        return match.groupValues[1] to match.groupValues[2]
    }

    override fun isAvailableToDownload(selectedCinemaUrl: String?, type: MovieType): Boolean {
        return type == MovieType.CINEMA
    }

    override suspend fun saveOrder(order: String) = withContext(dispatcher) {
        repository.setOrder(OrderKey.NORMAL, order)
    }

    override suspend fun saveHistoryOrder(order: String) = withContext(dispatcher) {
        repository.setOrder(OrderKey.HISTORY, order)
    }

    override suspend fun saveFavoriteOrder(order: String) {
        repository.setOrder(OrderKey.FAVORITE, order)
    }

    override suspend fun getOrder(isHistory: Boolean): String = withContext(dispatcher) {
        val defaultOrder =
            if (isHistory) AppConstants.Order.LAST_TIME else AppConstants.Order.YEAR_DESC
        val orderPreference = if (isHistory) repository.historyOrderPref else repository.orderPref
        orderPreference.ifBlank { defaultOrder }
    }

    override fun getFavoriteMovies(
        search: String,
        order: String,
        searchType: String
    ): Flow<PagingData<ViewMovie>> =
        repository.getFavoriteMoviesPager(search, order, searchType).flow

    override fun isFavoriteEmpty(): Flow<DataResult<Boolean>> = doAsync(dispatcher) {
        repository.isFavoriteEmpty()
    }

    override fun clearAllFavorites(): Flow<DataResult<Unit>> = doAsync(dispatcher) {
        repository.clearAllFavorites()
    }

    override fun onFavoriteToggle(movieId: Long): Flow<DataResult<Boolean>> = doAsync(dispatcher) {
        repository.toggleFavorite(movieId)
    }
}
