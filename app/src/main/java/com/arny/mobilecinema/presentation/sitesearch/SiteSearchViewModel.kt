package com.arny.mobilecinema.presentation.sitesearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.jsoupupdate.JsoupUpdateInteractor
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SimpleIntRange
import com.arny.mobilecinema.domain.models.SiteSearchItem
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.domain.search.SearchFilters
import com.arny.mobilecinema.domain.search.SearchHit
import com.arny.mobilecinema.domain.search.SearchMerge
import com.arny.mobilecinema.domain.search.SearchOrigin
import com.arny.mobilecinema.domain.search.SearchRelevance
import com.arny.mobilecinema.domain.search.SearchSource
import com.arny.mobilecinema.presentation.utils.BufferedChannel
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ResourceString
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Состояние единого поиска (SRCH-02): локальный каталог и сайт в одном списке.
 *
 * Локальный и внешний источники работают независимо: ошибка сайта не стирает локальные
 * результаты и не превращает весь экран в «Ошибку» (аудит v2, §4.2).
 */
data class SearchUiState(
    val query: String = "",
    val source: SearchSource = SearchSource.ALL,
    val filters: SearchFilters = SearchFilters(),
    val hits: List<SearchHit> = emptyList(),
    val isLoadingLocal: Boolean = false,
    val isLoadingRemote: Boolean = false,
    val isOpening: Boolean = false,
    val searched: Boolean = false,
    val localError: Boolean = false,
    val remoteError: Boolean = false,
    val filmsPage: Int = 0,
    val hasMoreFilms: Boolean = false,
    val genres: List<String> = emptyList(),
    val yearsRange: SimpleIntRange? = null,
) {
    val isLoading: Boolean get() = isLoadingLocal || isLoadingRemote

    /** Ни один источник ничего не отдал: показываем «Ничего не найдено», а не пустоту. */
    val isEmpty: Boolean get() = searched && !isLoading && hits.isEmpty()

    /** Оба источника упали — только тогда экран целиком ошибка. */
    val isFailed: Boolean
        get() = searched && !isLoading && hits.isEmpty() &&
                localError && remoteError

    /**
     * Фильтры умеет только локальный каталог. Когда они активны, а источник включает сайт,
     * экран обязан сказать, что внешняя выдача не отфильтрована (аудит v2, §4.1).
     */
    val filtersNotAppliedToSite: Boolean
        get() = filters.isActive && source != SearchSource.LOCAL
}

/**
 * Поиск: локальный каталог (Room) + сайт (Jsoup). Нужен для карточек, которых нет
 * в локальной базе, и для честного сравнения двух источников в одном списке.
 */
class SiteSearchViewModel(
    private val jsoupInteractor: JsoupUpdateInteractor,
    private val moviesInteractor: MoviesInteractor,
) : ViewModel() {

    private companion object {
        /** Столько карточек фильмов сайт отдаёт на страницу результатов. */
        const val FILMS_PAGE_SIZE = 10

        /** Сколько локальных результатов показываем без пагинации. */
        const val LOCAL_LIMIT = 40
    }

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()

    private val _toast = BufferedChannel<IWrappedString>()
    val toast = _toast.receiveAsFlow()

    private val _openMovie = BufferedChannel<Long>()
    val openMovie = _openMovie.receiveAsFlow()

    /** Текущий запрос: новый запрос отменяет предыдущий, чтобы устаревший ответ не перетёр свежий. */
    private var searchJob: Job? = null

    init {
        loadFilterData()
    }

    /** Жанры и границы годов для панели фильтров — один раз на экран. */
    private fun loadFilterData() {
        viewModelScope.launch {
            val genres = runCatching { moviesInteractor.loadCanonicalGenres() }.getOrDefault(emptyList())
            val years = runCatching { moviesInteractor.getMinMaxYears() }.getOrNull()
            _uiState.update { it.copy(genres = genres, yearsRange = years) }
        }
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun setSource(source: SearchSource) {
        if (_uiState.value.source == source) return
        _uiState.update { it.copy(source = source) }
        // Смена источника при готовом запросе сразу пересобирает выдачу.
        if (_uiState.value.query.isNotBlank()) {
            search()
        }
    }

    fun setFilters(filters: SearchFilters) {
        _uiState.update { it.copy(filters = filters) }
        if (_uiState.value.query.isNotBlank()) {
            search()
        }
    }

    fun resetFilters() {
        setFilters(_uiState.value.filters.reset())
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) {
            _uiState.update {
                it.copy(hits = emptyList(), searched = false, localError = false, remoteError = false)
            }
            return
        }
        val source = _uiState.value.source
        val filters = _uiState.value.filters
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    hits = emptyList(),
                    searched = true,
                    isLoadingLocal = source != SearchSource.REMOTE,
                    isLoadingRemote = source != SearchSource.LOCAL,
                    localError = false,
                    remoteError = false,
                    filmsPage = 0,
                    hasMoreFilms = false,
                )
            }

            val local = if (source != SearchSource.REMOTE) {
                loadLocal(query, filters)
            } else {
                LocalResult(hits = emptyList(), failed = false)
            }
            // Локальная выдача показывается сразу, не дожидаясь сайта.
            if (_uiState.value.query.trim() != query) return@launch
            if (local.hits.isNotEmpty()) {
                _uiState.update { it.copy(hits = local.hits, isLoadingLocal = false) }
            }

            val remote = if (source != SearchSource.LOCAL) {
                loadRemote(query, page = 1)
            } else {
                RemoteResult(items = emptyList(), failed = false)
            }
            if (_uiState.value.query.trim() != query) return@launch

            val remoteHits = remote.items.map { it.toHit(query) }
            val merged = SearchMerge.merge(local.hits, remoteHits)
            _uiState.update {
                it.copy(
                    hits = merged,
                    isLoadingLocal = false,
                    isLoadingRemote = false,
                    localError = local.failed,
                    remoteError = remote.failed,
                    filmsPage = if (remote.failed) 0 else 1,
                    hasMoreFilms = remote.items.count { item -> item.type == MovieType.CINEMA } >=
                            FILMS_PAGE_SIZE,
                )
            }
            if (remote.failed) {
                _toast.trySend(ResourceString(R.string.site_search_error))
            }
        }
    }

    /** Повтор последнего запроса из состояния ошибки (кнопка «Повторить»). */
    fun retry() {
        if (_uiState.value.isLoading) return
        search()
    }

    /** Следующая страница поиска фильмов сайта (у локальной выдачи пагинации нет). */
    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMoreFilms || state.isLoading || state.isLoadingRemote || state.isOpening) {
            return
        }
        if (state.source == SearchSource.LOCAL) return
        val query = state.query.trim()
        if (query.isBlank()) return
        viewModelScope.launch {
            val nextPage = state.filmsPage + 1
            _uiState.update { it.copy(isLoadingRemote = true) }
            val remote = loadRemote(query, nextPage)
            // Пока грузилась страница, запрос или источник могли смениться.
            if (_uiState.value.query.trim() != query) return@launch
            if (remote.failed) {
                _uiState.update { it.copy(isLoadingRemote = false, hasMoreFilms = false) }
                _toast.trySend(ResourceString(R.string.site_search_error))
                return@launch
            }
            val films = remote.items.filter { item -> item.type == MovieType.CINEMA }
            _uiState.update { current ->
                val merged = SearchMerge.merge(
                    local = current.hits.filter { hit -> hit.inCatalog },
                    remote = (current.hits.filterNot { hit -> hit.inCatalog } +
                            films.map { it.toHit(query) })
                        .distinctBy { hit -> hit.siteRef },
                )
                current.copy(
                    isLoadingRemote = false,
                    hits = merged,
                    filmsPage = nextPage,
                    hasMoreFilms = films.size >= FILMS_PAGE_SIZE,
                )
            }
        }
    }

    /** Открытие результата: локальный — сразу в детали, внешний — сначала сохранить карточку. */
    fun openHit(hit: SearchHit) {
        if (_uiState.value.isOpening) return
        val stableId = hit.stableId
        if (stableId != null && stableId > 0) {
            _openMovie.trySend(stableId)
            return
        }
        if (hit.siteRef.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOpening = true) }
            when (val result = jsoupInteractor.fetchAndSaveMovieCard(hit.siteRef)) {
                is DataResult.Success -> {
                    _uiState.update { it.copy(isOpening = false) }
                    _openMovie.trySend(result.result)
                }

                is DataResult.Error -> {
                    Timber.e(result.throwable, "search open ${hit.siteRef} failed")
                    _uiState.update { it.copy(isOpening = false) }
                    _toast.trySend(ResourceString(R.string.site_search_open_error))
                }
            }
        }
    }

    private suspend fun loadLocal(query: String, filters: SearchFilters): LocalResult {
        val result = runCatching {
            moviesInteractor.searchLocal(
                query = query,
                kinds = filters.movieTypes,
                genre = filters.genre,
                country = filters.country,
                years = filters.yearRange,
                order = filters.sort.order,
                limit = LOCAL_LIMIT,
            )
        }
        return result.fold(
            onSuccess = { movies -> LocalResult(movies.map { it.toHit(query) }, failed = false) },
            onFailure = { error ->
                Timber.e(error, "local search failed")
                LocalResult(emptyList(), failed = true)
            }
        )
    }

    private suspend fun loadRemote(query: String, page: Int): RemoteResult =
        when (val result = jsoupInteractor.searchOnSite(query = query, page = page)) {
            is DataResult.Success -> RemoteResult(items = result.result, failed = false)
            is DataResult.Error -> {
                Timber.e(result.throwable, "site search failed")
                RemoteResult(items = emptyList(), failed = true)
            }
        }

    private fun ViewMovie.toHit(query: String): SearchHit = SearchHit(
        origin = SearchOrigin.CATALOG,
        title = title,
        originalTitle = "",
        year = year,
        kind = MovieType.fromValue(type),
        posterUrl = img,
        rating = ratingImdb.toFloat(),
        matchScore = SearchRelevance.score(query, title),
        stableId = dbId,
    )

    private fun SiteSearchItem.toHit(query: String): SearchHit = SearchHit(
        origin = SearchOrigin.SITE,
        title = title,
        originalTitle = origTitle,
        year = year,
        kind = type,
        posterUrl = img,
        description = description,
        rating = rating,
        matchScore = SearchRelevance.score(query, title, origTitle),
        siteRef = pageUrl,
    )

    private data class RemoteResult(
        val items: List<SiteSearchItem>,
        val failed: Boolean,
    )

    private data class LocalResult(
        val hits: List<SearchHit>,
        val failed: Boolean,
    )
}
