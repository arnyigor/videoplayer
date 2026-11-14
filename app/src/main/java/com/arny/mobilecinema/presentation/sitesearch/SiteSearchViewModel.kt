package com.arny.mobilecinema.presentation.sitesearch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.jsoupupdate.JsoupUpdateInteractor
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SimpleIntRange
import com.arny.mobilecinema.domain.models.SiteSearchItem
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.domain.search.SearchFilters
import com.arny.mobilecinema.domain.search.RatingSource
import com.arny.mobilecinema.domain.search.SearchHit
import com.arny.mobilecinema.domain.search.SearchMerge
import com.arny.mobilecinema.domain.search.SearchOrigin
import com.arny.mobilecinema.domain.search.SearchPaging
import com.arny.mobilecinema.domain.search.SearchRelevance
import com.arny.mobilecinema.domain.search.SearchSource
import com.arny.mobilecinema.presentation.utils.BufferedChannel
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ResourceString
import kotlinx.coroutines.CancellationException
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
/**
 * Состояние одного источника (S-015). Раньше это были четыре несвязанных флага
 * (`isLoadingLocal`, `localError`, `searched`, `hits`), и «пусто» отличалось от «не искали»
 * только по их комбинации — легко было показать пустоту вместо загрузки.
 */
enum class ResultSection { IDLE, LOADING, SUCCESS, EMPTY, ERROR }

data class SearchUiState(
    val query: String = "",
    val searchType: String = AppConstants.SearchType.TITLE,
    /** Mode used for the displayed results, not the editable next search. */
    val executedSearchType: String = AppConstants.SearchType.TITLE,
    val source: SearchSource = SearchSource.ALL,
    /**
     * Применённые фильтры. Черновик живёт в [SearchFiltersSheet] и попадает сюда только по
     * кнопке Apply — отдельного draft-поля в состоянии не нужно, иначе появятся две правды.
     */
    val filters: SearchFilters = SearchFilters(),
    val hits: List<SearchHit> = emptyList(),
    val isLoadingLocal: Boolean = false,
    val isLoadingRemote: Boolean = false,
    val isOpening: Boolean = false,
    val searched: Boolean = false,
    val localError: Boolean = false,
    val remoteError: Boolean = false,
    /**
     * Идентификатор актуального запроса (S-015). Ответы с другим идентификатором отбрасываются,
     * поэтому устаревшая выдача не может перетереть свежую при смене scope или фильтров.
     */
    val pendingRequestId: Long = 0L,
    val filmsPage: Int = 0,
    val hasMoreFilms: Boolean = false,
    val genres: List<String> = emptyList(),
    val yearsRange: SimpleIntRange? = null,
    val countries: List<String> = emptyList(),
) {
    /** Состояние локального источника — для раздельной индикации и текстов. */
    val localSection: ResultSection
        get() = when {
            source == SearchSource.REMOTE -> ResultSection.IDLE
            isLoadingLocal -> ResultSection.LOADING
            localError -> ResultSection.ERROR
            !searched -> ResultSection.IDLE
            hits.any { it.inCatalog } -> ResultSection.SUCCESS
            else -> ResultSection.EMPTY
        }

    /** Состояние сайта: отдельная ветка, чтобы ошибка одного не выглядела ошибкой другого. */
    val remoteSection: ResultSection
        get() = when {
            source == SearchSource.LOCAL -> ResultSection.IDLE
            isLoadingRemote -> ResultSection.LOADING
            remoteError -> ResultSection.ERROR
            !searched -> ResultSection.IDLE
            hits.any { !it.inCatalog } -> ResultSection.SUCCESS
            else -> ResultSection.EMPTY
        }

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
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private companion object {
        /** Сколько локальных результатов показываем без пагинации. */
        const val LOCAL_LIMIT = 40

        const val QUERY = "search_query"
        const val SEARCH_TYPE = "search_type"
        const val SOURCE = "search_source"
        const val FILTERS = "search_filters"
        const val EXECUTED_QUERY = "search_executed_query"
        const val EXECUTED_TYPE = "search_executed_type"
        const val INITIALIZED = "search_initialized"
    }

    private val _uiState = MutableStateFlow(SearchUiState(
        query = savedStateHandle[QUERY] ?: "",
        searchType = savedStateHandle[SEARCH_TYPE] ?: AppConstants.SearchType.TITLE,
        source = SearchSource.entries.firstOrNull { it.name == savedStateHandle.get<String>(SOURCE) } ?: SearchSource.ALL,
        filters = SearchFilters.decode(savedStateHandle[FILTERS]),
    ))
    private var initialized = savedStateHandle.get<Boolean>(INITIALIZED) == true || savedStateHandle.contains(QUERY)
    val uiState = _uiState.asStateFlow()

    private val _toast = BufferedChannel<IWrappedString>()
    val toast = _toast.receiveAsFlow()

    private val _openMovie = BufferedChannel<Long>()
    val openMovie = _openMovie.receiveAsFlow()

    /** Текущий запрос: новый запрос отменяет предыдущий, чтобы устаревший ответ не перетёр свежий. */
    private var searchJob: Job? = null

    // Paging belongs to the submitted search, not to the editable text field.
    private var activeQuery: String? = null
    private var activeSearchType: String = AppConstants.SearchType.TITLE
    // Include URLs removed by SearchMerge because a catalog card already represents them.
    private val seenRemoteUrls = mutableSetOf<String>()

    /**
     * Идентификатор актуального запроса (S-015). Сравнения строки запроса мало: при смене scope
     * или фильтров строка прежняя, а выдача должна быть новой. Ответ с чужим id отбрасывается.
     */
    private var requestId: Long = 0L

    init {
        loadFilterData()
        val executedQuery = savedStateHandle.get<String>(EXECUTED_QUERY)
        if (!executedQuery.isNullOrBlank()) {
            executeSearch(executedQuery, savedStateHandle[EXECUTED_TYPE] ?: _uiState.value.searchType)
        }
    }

    /** Navigation arguments are initial defaults, never an override of a restored search. */
    fun initialize(query: String, searchType: String, source: SearchSource? = null) {
        if (initialized) return
        initialized = true
        savedStateHandle[INITIALIZED] = true
        setSearchType(searchType)
        source?.let(::setSource)
        setQuery(query)
        if (query.isNotBlank()) search()
    }

    /** Жанры и границы годов для панели фильтров — один раз на экран. */
    private fun loadFilterData() {
        viewModelScope.launch {
            val genres = runCatching { moviesInteractor.loadCanonicalGenres() }.getOrDefault(emptyList())
            val years = runCatching { moviesInteractor.getMinMaxYears() }.getOrNull()
            val countries = runCatching { moviesInteractor.getCountries() }.getOrNull().orEmpty()
            _uiState.update { it.copy(genres = genres, yearsRange = years, countries = countries) }
        }
    }

    fun setSearchType(searchType: String) {
        savedStateHandle[SEARCH_TYPE] = searchType
        _uiState.update { it.copy(searchType = searchType) }
    }

    fun setQuery(query: String) {
        savedStateHandle[QUERY] = query
        _uiState.update { it.copy(query = query) }
        if (query.isBlank()) search()
    }

    fun setSource(source: SearchSource) {
        if (_uiState.value.source == source) return
        savedStateHandle[SOURCE] = source.name
        _uiState.update { it.copy(source = source) }
        // Смена источника при готовом запросе сразу пересобирает выдачу.
        if (_uiState.value.query.isNotBlank()) {
            search()
        }
    }

    fun setFilters(filters: SearchFilters) {
        savedStateHandle[FILTERS] = filters.encode()
        _uiState.update { it.copy(filters = filters) }
        if (_uiState.value.query.isNotBlank()) {
            search()
        }
    }

    fun resetFilters() {
        setFilters(_uiState.value.filters.reset())
    }

    fun search() {
        executeSearch(_uiState.value.query.trim(), _uiState.value.searchType)
    }

    private fun executeSearch(query: String, searchType: String) {
        searchJob?.cancel()
        val generation = ++requestId
        seenRemoteUrls.clear()
        if (query.isBlank()) {
            savedStateHandle[EXECUTED_QUERY] = null
            savedStateHandle[EXECUTED_TYPE] = null
            activeQuery = null
            _uiState.update {
                it.copy(
                    hits = emptyList(), searched = false, localError = false, remoteError = false,
                    isLoadingLocal = false, isLoadingRemote = false,
                    filmsPage = 0, hasMoreFilms = false, pendingRequestId = generation,
                )
            }
            return
        }
        val filters = _uiState.value.filters
        // Фильтры умеет только каталог; сайт их не поддерживает и отдал бы нефильтрованную
        // выдачу. При активных фильтрах в режиме «Везде» поиск идёт только по каталогу —
        // иначе пользователь видит «ничего не изменилось».
        val source = _uiState.value.source.let {
            if (filters.isActive && it == SearchSource.ALL) SearchSource.LOCAL else it
        }
        activeQuery = query
        activeSearchType = searchType
        savedStateHandle[EXECUTED_QUERY] = query
        savedStateHandle[EXECUTED_TYPE] = searchType
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    hits = emptyList(),
                    searched = true,
                    executedSearchType = searchType,
                    isLoadingLocal = source != SearchSource.REMOTE,
                    isLoadingRemote = source != SearchSource.LOCAL,
                    localError = false,
                    remoteError = false,
                    pendingRequestId = generation,
                    filmsPage = 0,
                    hasMoreFilms = false,
                )
            }

            val local = if (source != SearchSource.REMOTE) {
                loadLocal(query, filters, searchType)
            } else {
                LocalResult(hits = emptyList(), failed = false)
            }
            // Локальная выдача показывается сразу, не дожидаясь сайта.
            if (generation != requestId) return@launch
            _uiState.update {
                it.copy(hits = local.hits, isLoadingLocal = false, localError = local.failed)
            }

            val remote = if (source != SearchSource.LOCAL) {
                loadRemote(query, page = 1, searchType = searchType)
            } else {
                RemoteResult(items = emptyList(), failed = false)
            }
            if (generation != requestId) return@launch

            seenRemoteUrls.addAll(remote.items.map { it.pageUrl }.filter { it.isNotBlank() })
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
                    hasMoreFilms = !remote.failed && SearchPaging.hasMore(remote.items),
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

    /** Следующая страница поиска сайта: и фильмы, и сериалы (у локальной выдачи пагинации нет). */
    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMoreFilms || state.isLoading || state.isLoadingRemote || state.isOpening) {
            return
        }
        // Пагинация сайта нефильтрованная: при активных фильтрах её нельзя подмешивать.
        if (state.source == SearchSource.LOCAL) return
        if (state.filters.isActive) return
        val query = activeQuery ?: return
        val searchType = activeSearchType
        val generation = requestId
        val nextPage = state.filmsPage + 1
        // Reserve the page before launching so two scroll events cannot request it twice.
        _uiState.update { it.copy(isLoadingRemote = true) }
        searchJob = viewModelScope.launch {
            val remote = loadRemote(query, nextPage, searchType)
            // Пока грузилась страница, запрос или источник могли смениться.
            if (generation != requestId) return@launch
            if (remote.failed) {
                _uiState.update { it.copy(isLoadingRemote = false, hasMoreFilms = false) }
                _toast.trySend(ResourceString(R.string.site_search_error))
                return@launch
            }
            val newHits = remote.items
                .filter { it.pageUrl.isNotBlank() && it.pageUrl !in seenRemoteUrls }
                .distinctBy { it.pageUrl }
                .map { it.toHit(query) }
            seenRemoteUrls.addAll(remote.items.map { it.pageUrl }.filter { it.isNotBlank() })
            _uiState.update { current ->
                val merged = SearchMerge.merge(
                    local = current.hits.filter { hit -> hit.inCatalog },
                    remote = (current.hits.filterNot { hit -> hit.inCatalog } + newHits)
                        .distinctBy { hit -> hit.siteRef },
                )
                current.copy(
                    isLoadingRemote = false,
                    hits = merged,
                    filmsPage = nextPage,
                    hasMoreFilms = newHits.isNotEmpty() && SearchPaging.hasMore(remote.items),
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

    private suspend fun loadLocal(query: String, filters: SearchFilters, searchType: String): LocalResult {
        val result = runCatching {
            moviesInteractor.searchLocal(
                query = query,
                kinds = filters.movieTypes,
                genres = filters.genres,
                country = filters.country,
                years = filters.yearRange,
                order = filters.sort.order,
                limit = LOCAL_LIMIT,
                searchType = searchType,
            )
        }
        return result.fold(
            onSuccess = { movies -> LocalResult(movies.map { it.toHit(query) }, failed = false) },
            onFailure = { error ->
                if (error is CancellationException) throw error
                Timber.e(error, "local search failed")
                LocalResult(emptyList(), failed = true)
            }
        )
    }

    private suspend fun loadRemote(query: String, page: Int, searchType: String): RemoteResult =
        try {
            when (val result = jsoupInteractor.searchOnSite(query = query, page = page, searchType = searchType)) {
                is DataResult.Success -> RemoteResult(items = result.result, failed = false)
                is DataResult.Error -> {
                    if (result.throwable is CancellationException) throw result.throwable
                    Timber.e(result.throwable, "site search failed")
                    RemoteResult(items = emptyList(), failed = true)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.e(error, "site search failed")
            RemoteResult(items = emptyList(), failed = true)
        }

    private fun ViewMovie.toHit(query: String): SearchHit = SearchHit(
        origin = SearchOrigin.CATALOG,
        title = title,
        originalTitle = "",
        year = year,
        kind = MovieType.fromValue(type),
        posterUrl = img,
        rating = ratingImdb.toFloat(),
        ratingSource = if (ratingImdb > 0.0) RatingSource.IMDB else RatingSource.NONE,
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
        ratingSource = if (rating > 0f) RatingSource.SITE else RatingSource.NONE,
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
