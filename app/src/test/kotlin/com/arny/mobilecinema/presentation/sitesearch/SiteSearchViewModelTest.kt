package com.arny.mobilecinema.presentation.sitesearch

import androidx.lifecycle.SavedStateHandle
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.search.SearchFilters
import com.arny.mobilecinema.domain.search.SearchSort

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.jsoupupdate.JsoupUpdateInteractor
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.models.SimpleIntRange
import com.arny.mobilecinema.domain.models.SiteSearchItem
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.domain.search.SearchHit
import com.arny.mobilecinema.domain.search.SearchOrigin
import com.arny.mobilecinema.domain.search.SearchSource
import com.nhaarman.mockitokotlin2.mock
import com.arny.mobilecinema.domain.models.MovieType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * S-012: локальные результаты не ждут медленного сайта, а ошибки источников различимы.
 *
 * Сайт «зависает» на [CompletableDeferred]: пока он не ответил, локальная выдача уже
 * должна быть на экране, а индикатор сайта — гореть отдельно от индикатора каталога.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SiteSearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // mockito-kotlin 2.2 не умеет suspend-ответы, поэтому нужные методы переопределены
    // через делегирование: остальное поведение интерфейса берёт заглушка mock().
    private fun movies(local: List<ViewMovie>, fail: Boolean = false): MoviesInteractor =
        object : MoviesInteractor by mock() {
            override suspend fun loadCanonicalGenres(): List<String> = emptyList()
            override suspend fun getMinMaxYears(): SimpleIntRange = SimpleIntRange(1900, 2030)
            override suspend fun searchLocal(
                query: String,
                kinds: List<MovieType>,
                genres: List<String>,
                country: String?,
                years: SimpleIntRange?,
                order: String,
                limit: Int,
                offset: Int,
                searchType: String,
            ): List<ViewMovie> = if (fail) error("db down") else local
        }

    private fun site(gate: CompletableDeferred<DataResult<List<SiteSearchItem>>>): JsoupUpdateInteractor =
        object : JsoupUpdateInteractor by mock() {
            override suspend fun searchOnSite(query: String, page: Int, searchType: String): DataResult<List<SiteSearchItem>> =
                gate.await()
        }

    private fun restoredHandle(handle: SavedStateHandle) = SavedStateHandle(
        handle.keys().associateWith { handle.get<Any?>(it) }
    )

    @Test
    fun `restoration preserves query source filters and sort instead of navigation defaults`() = scope.runTest {
        val handle = SavedStateHandle()
        val filters = SearchFilters(kinds = listOf(MovieType.CINEMA), genres = listOf("Action"), country = "USA",
            yearFrom = 1999, yearTo = 2020, sort = SearchSort.RATING)
        val remote = site(CompletableDeferred(DataResult.Success(emptyList())))
        val first = SiteSearchViewModel(remote, movies(emptyList()), handle)
        first.initialize("matrix", AppConstants.SearchType.TITLE, SearchSource.LOCAL)
        first.setFilters(filters)
        advanceUntilIdle()
        val restored = SiteSearchViewModel(remote, movies(emptyList()), restoredHandle(handle))
        restored.initialize("old navigation query", AppConstants.SearchType.DIRECTORS, SearchSource.REMOTE)
        advanceUntilIdle()
        assertEquals("matrix", restored.uiState.value.query)
        assertEquals(SearchSource.LOCAL, restored.uiState.value.source)
        assertEquals(AppConstants.SearchType.TITLE, restored.uiState.value.searchType)
        assertEquals(filters, restored.uiState.value.filters)
        assertTrue(restored.uiState.value.searched)
    }

    @Test
    fun `unsubmitted draft survives without silently executing it`() = scope.runTest {
        var requests = 0
        val remote = object : JsoupUpdateInteractor by mock() {
            override suspend fun searchOnSite(query: String, page: Int, searchType: String): DataResult<List<SiteSearchItem>> {
                requests++
                return DataResult.Success(emptyList())
            }
        }
        val handle = SavedStateHandle()
        val first = SiteSearchViewModel(remote, movies(emptyList()), handle)
        first.initialize("", AppConstants.SearchType.TITLE, SearchSource.REMOTE)
        first.setQuery("draft")
        val restored = SiteSearchViewModel(remote, movies(emptyList()), restoredHandle(handle))
        restored.initialize("navigation default", AppConstants.SearchType.TITLE)
        advanceUntilIdle()
        assertEquals("draft", restored.uiState.value.query)
        assertFalse(restored.uiState.value.searched)
        assertEquals(0, requests)
    }

    @Test
    fun `restored paging belongs to executed query and type while preserving draft`() = scope.runTest {
        val calls = mutableListOf<Triple<String, Int, String>>()
        val remote = object : JsoupUpdateInteractor by mock() {
            override suspend fun searchOnSite(query: String, page: Int, searchType: String): DataResult<List<SiteSearchItem>> {
                calls += Triple(query, page, searchType)
                return DataResult.Success((1..10).map {
                    SiteSearchItem(title = "$query $it", pageUrl = "/films/${page * 100 + it}", type = MovieType.CINEMA)
                })
            }
        }
        val handle = SavedStateHandle()
        val first = SiteSearchViewModel(remote, movies(emptyList()), handle)
        first.initialize("matrix", AppConstants.SearchType.TITLE, SearchSource.REMOTE)
        advanceUntilIdle()
        first.setQuery("alien")
        first.setSearchType(AppConstants.SearchType.DIRECTORS)
        val restored = SiteSearchViewModel(remote, movies(emptyList()), restoredHandle(handle))
        advanceUntilIdle()
        restored.loadMore()
        advanceUntilIdle()
        assertEquals("alien", restored.uiState.value.query)
        assertEquals(AppConstants.SearchType.DIRECTORS, restored.uiState.value.searchType)
        assertEquals(listOf(Triple("matrix", 1, AppConstants.SearchType.TITLE),
            Triple("matrix", 1, AppConstants.SearchType.TITLE), Triple("matrix", 2, AppConstants.SearchType.TITLE)), calls)
    }

    @Test
    fun `initializing existing view model does not reset or repeat search`() = scope.runTest {
        var requests = 0
        val remote = object : JsoupUpdateInteractor by mock() {
            override suspend fun searchOnSite(query: String, page: Int, searchType: String): DataResult<List<SiteSearchItem>> {
                requests++
                return DataResult.Success(emptyList())
            }
        }
        val vm = SiteSearchViewModel(remote, movies(emptyList()))
        vm.initialize("matrix", AppConstants.SearchType.TITLE, SearchSource.REMOTE)
        advanceUntilIdle()
        vm.setQuery("new draft")
        vm.initialize("matrix", AppConstants.SearchType.TITLE, SearchSource.LOCAL)
        advanceUntilIdle()
        assertEquals(1, requests)
        assertEquals("new draft", vm.uiState.value.query)
        assertEquals(SearchSource.REMOTE, vm.uiState.value.source)
    }

    @Test
    fun `cleared query is not resubmitted on restoration`() = scope.runTest {
        val handle = SavedStateHandle()
        val remote = site(CompletableDeferred(DataResult.Success(emptyList())))
        val first = SiteSearchViewModel(remote, movies(emptyList()), handle)
        first.initialize("matrix", AppConstants.SearchType.TITLE)
        advanceUntilIdle()
        first.setQuery("")
        val restored = SiteSearchViewModel(remote, movies(emptyList()), restoredHandle(handle))
        advanceUntilIdle()
        assertEquals("", restored.uiState.value.query)
        assertFalse(restored.uiState.value.searched)
        assertFalse(restored.uiState.value.isLoading)
    }

    @Test
    fun `clearing query cancels search and resets loading and paging`() = scope.runTest {
        var cancelled = false
        val remote = object : JsoupUpdateInteractor by mock() {
            override suspend fun searchOnSite(query: String, page: Int, searchType: String): DataResult<List<SiteSearchItem>> {
                try {
                    return CompletableDeferred<DataResult<List<SiteSearchItem>>>().await()
                } finally {
                    cancelled = true
                }
            }
        }
        val viewModel = SiteSearchViewModel(remote, movies(emptyList()))
        viewModel.setQuery("matrix")
        viewModel.search()
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoadingRemote)
        viewModel.setQuery("")
        viewModel.search()
        runCurrent()
        assertTrue(cancelled)
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.searched)
        assertFalse(state.hasMoreFilms)
        assertEquals(0, state.filmsPage)
        assertTrue(state.hits.isEmpty())
    }

    @Test
    fun `empty local section completes before slow site`() = scope.runTest {
        val gate = CompletableDeferred<DataResult<List<SiteSearchItem>>>()
        val viewModel = SiteSearchViewModel(site(gate), movies(emptyList()))
        viewModel.setQuery("matrix")
        viewModel.search()
        runCurrent()
        assertEquals(ResultSection.EMPTY, viewModel.uiState.value.localSection)
        assertEquals(ResultSection.LOADING, viewModel.uiState.value.remoteSection)
        gate.complete(DataResult.Success(emptyList()))
        advanceUntilIdle()
    }

    @Test
    fun `local error is published before slow site`() = scope.runTest {
        val gate = CompletableDeferred<DataResult<List<SiteSearchItem>>>()
        val viewModel = SiteSearchViewModel(site(gate), movies(emptyList(), fail = true))
        viewModel.setQuery("matrix")
        viewModel.search()
        runCurrent()
        assertEquals(ResultSection.ERROR, viewModel.uiState.value.localSection)
        assertEquals(ResultSection.LOADING, viewModel.uiState.value.remoteSection)
        gate.complete(DataResult.Success(emptyList()))
        advanceUntilIdle()
    }

    @Test
    fun `paging uses executed query instead of unsubmitted text and starts only once`() = scope.runTest {
        val calls = mutableListOf<Pair<String, Int>>()
        val remote = object : JsoupUpdateInteractor by mock() {
            override suspend fun searchOnSite(query: String, page: Int, searchType: String): DataResult<List<SiteSearchItem>> {
                calls += query to page
                return DataResult.Success((1..10).map {
                    SiteSearchItem(title = "$query $page $it", pageUrl = "/films/${page * 100 + it}", type = MovieType.CINEMA)
                })
            }
        }
        val viewModel = SiteSearchViewModel(remote, movies(emptyList()))
        viewModel.setSource(SearchSource.REMOTE)
        viewModel.setQuery("matrix")
        viewModel.search()
        advanceUntilIdle()
        viewModel.setQuery("alien")
        viewModel.loadMore()
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("matrix" to 1, "matrix" to 2), calls)
        assertEquals(20, viewModel.uiState.value.hits.size)
    }

    @Test
    fun `unexpected remote exception finishes loading with source error`() = scope.runTest {
        val remote = object : JsoupUpdateInteractor by mock() {
            override suspend fun searchOnSite(query: String, page: Int, searchType: String): DataResult<List<SiteSearchItem>> =
                error("unexpected network failure")
        }
        val viewModel = SiteSearchViewModel(remote, movies(emptyList()))
        viewModel.setQuery("matrix")
        viewModel.search()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(ResultSection.ERROR, viewModel.uiState.value.remoteSection)
    }

    @Test
    fun `local hits appear while slow site is still loading`() = scope.runTest {
        val gate = CompletableDeferred<DataResult<List<SiteSearchItem>>>()
        val viewModel = SiteSearchViewModel(site(gate), movies(listOf(ViewMovie(dbId = 1, title = "Матрица"))))
        viewModel.setQuery("матрица")
        viewModel.search()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(1, state.hits.size, "локальная выдача должна быть видна до ответа сайта")
        assertFalse(state.isLoadingLocal, "каталог уже ответил")
        assertTrue(state.isLoadingRemote, "сайт ещё грузится")

        gate.complete(DataResult.Success(emptyList()))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoadingRemote)
        assertEquals(1, viewModel.uiState.value.hits.size)
    }

    @Test
    fun `site error keeps local hits and is reported separately`() = scope.runTest {
        val gate = CompletableDeferred<DataResult<List<SiteSearchItem>>>()
        val viewModel = SiteSearchViewModel(site(gate), movies(listOf(ViewMovie(dbId = 2, title = "Матрица"))))
        viewModel.setQuery("матрица")
        viewModel.search()
        gate.complete(DataResult.Error(RuntimeException("timeout")))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.hits.size, "ошибка сайта не стирает каталог")
        assertTrue(state.remoteError)
        assertFalse(state.localError)
        assertFalse(state.isFailed, "экран целиком не в ошибке: каталог ответил")
    }

    @Test
    fun `local error is distinguishable from site error`() = scope.runTest {
        val gate = CompletableDeferred<DataResult<List<SiteSearchItem>>>()
        val viewModel = SiteSearchViewModel(site(gate), movies(emptyList(), fail = true))
        viewModel.setQuery("матрица")
        viewModel.setSource(SearchSource.LOCAL)
        viewModel.search()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.localError)
        assertFalse(state.remoteError)
    }

    private fun TestScope.runTest(block: suspend TestScope.() -> Unit) =
        kotlinx.coroutines.test.runTest(dispatcher) { block() }

    @Test
    fun `stale local result is dropped when scope changes with same query`() = scope.runTest {
        // Первый запрос: каталог «завис», сайт отвечает сразу.
        val localGate = CompletableDeferred<List<ViewMovie>>()
        val movies = object : MoviesInteractor by mock() {
            override suspend fun loadCanonicalGenres(): List<String> = emptyList()
            override suspend fun getMinMaxYears(): SimpleIntRange = SimpleIntRange(1900, 2030)
            override suspend fun searchLocal(
                query: String,
                kinds: List<MovieType>,
                genres: List<String>,
                country: String?,
                years: SimpleIntRange?,
                order: String,
                limit: Int,
                offset: Int,
                searchType: String,
            ): List<ViewMovie> = localGate.await()
        }
        val gate = CompletableDeferred<DataResult<List<SiteSearchItem>>>()
        val viewModel = SiteSearchViewModel(site(gate), movies)
        viewModel.setQuery("matrix")
        viewModel.search()
        runCurrent()

        // Пока каталог не ответил, переключаемся на «только сайт» с тем же запросом.
        viewModel.setSource(SearchSource.REMOTE)
        gate.complete(DataResult.Success(emptyList()))
        advanceUntilIdle()

        // Поздний ответ первого поколения не должен добавлять каталог в выдачу сайта.
        localGate.complete(listOf(ViewMovie(dbId = 7, title = "Матрица")))
        advanceUntilIdle()
        assertTrue(
            viewModel.uiState.value.hits.isEmpty(),
            "устаревшая локальная выдача попала в режим «только сайт»"
        )
    }

    @Test
    fun `new search resets paging so pages are not mixed`() = scope.runTest {
        // Каждый вызов сайта ждёт свой gate: ответ второго запроса можно удержать.
        val first = CompletableDeferred<DataResult<List<SiteSearchItem>>>()
        val second = CompletableDeferred<DataResult<List<SiteSearchItem>>>()
        val queue = ArrayDeque(listOf(first, second))
        val queuedSite = object : JsoupUpdateInteractor by mock() {
            override suspend fun searchOnSite(
                query: String,
                page: Int,
                searchType: String,
            ): DataResult<List<SiteSearchItem>> = queue.removeFirst().await()
        }
        val viewModel = SiteSearchViewModel(queuedSite, movies(emptyList()))
        viewModel.setQuery("matrix")
        viewModel.search()
        first.complete(DataResult.Success(emptyList()))
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filmsPage)

        // Новый запрос: пагинация обнуляется до ответа сайта, строки прошлого запроса уходят.
        viewModel.setQuery("матрица")
        viewModel.search()
        runCurrent()
        assertEquals(0, viewModel.uiState.value.filmsPage)
        assertTrue(
            viewModel.uiState.value.hits.isEmpty(),
            "выдача прошлого запроса не должна остаться"
        )
    }

    @Test
    fun `sections distinguish idle loading empty success and error`() {
        val empty = SearchUiState()
        assertEquals(ResultSection.IDLE, empty.localSection)
        assertEquals(ResultSection.IDLE, empty.remoteSection)

        val loading = SearchUiState(
            query = "matrix", source = SearchSource.ALL, searched = true,
            isLoadingLocal = true, isLoadingRemote = true
        )
        assertEquals(ResultSection.LOADING, loading.localSection)
        assertEquals(ResultSection.LOADING, loading.remoteSection)

        val mixed = SearchUiState(
            query = "matrix", source = SearchSource.ALL, searched = true,
            hits = listOf(
                SearchHit(origin = SearchOrigin.CATALOG, title = "Матрица", stableId = 1),
                SearchHit(origin = SearchOrigin.SITE, title = "Матрица: Воскрешение", siteRef = "/films/9"),
            ),
            localError = false, remoteError = true
        )
        assertEquals(ResultSection.SUCCESS, mixed.localSection)
        assertEquals(ResultSection.ERROR, mixed.remoteSection)

        val siteOnly = SearchUiState(query = "matrix", source = SearchSource.REMOTE, searched = true)
        assertEquals(ResultSection.IDLE, siteOnly.localSection)
        assertEquals(ResultSection.EMPTY, siteOnly.remoteSection)
    }

    @Test
    fun `pending request id follows the search`() = scope.runTest {
        val gate = CompletableDeferred<DataResult<List<SiteSearchItem>>>()
        val viewModel = SiteSearchViewModel(site(gate), movies(emptyList()))
        assertEquals(0L, viewModel.uiState.value.pendingRequestId)

        viewModel.setQuery("matrix")
        viewModel.search()
        runCurrent()
        val firstId = viewModel.uiState.value.pendingRequestId
        assertTrue(firstId > 0L, "id запроса должен увеличиться")

        viewModel.setQuery("матрица")
        viewModel.search()
        runCurrent()
        assertTrue(
            viewModel.uiState.value.pendingRequestId > firstId,
            "новый запрос должен получить новый id"
        )
    }
    @Test
    fun `actor type reaches both sources instead of title search`() = scope.runTest {
        var localType = ""
        var remoteType = ""
        val local = object : MoviesInteractor by movies(emptyList()) {
            override suspend fun searchLocal(
                query: String, kinds: List<MovieType>, genres: List<String>, country: String?,
                years: SimpleIntRange?, order: String, limit: Int, offset: Int, searchType: String,
            ): List<ViewMovie> {
                localType = searchType
                return listOf(ViewMovie(dbId = 42, title = "Different title"))
            }
        }
        val remote = object : JsoupUpdateInteractor by mock() {
            override suspend fun searchOnSite(query: String, page: Int, searchType: String): DataResult<List<SiteSearchItem>> {
                remoteType = searchType
                return DataResult.Success(emptyList())
            }
        }
        val viewModel = SiteSearchViewModel(remote, local)
        viewModel.setSearchType("actors")
        viewModel.setQuery("Fixture Actor")
        viewModel.search()
        advanceUntilIdle()
        assertEquals("actors", localType)
        assertEquals("actors", remoteType)
        assertEquals(1, viewModel.uiState.value.hits.size)
    }

    @Test
    fun `active filters in ALL mode search the catalog only and never call the site`() = scope.runTest {
        var remoteCalls = 0
        val remote = object : JsoupUpdateInteractor by mock() {
            override suspend fun searchOnSite(query: String, page: Int, searchType: String): DataResult<List<SiteSearchItem>> {
                remoteCalls++
                return DataResult.Success(listOf(SiteSearchItem(title = "Matrix reloaded", pageUrl = "/x")))
            }
        }
        // Каталог отдаёт пусто: у «матрицы» нет жанра «Аниме».
        val viewModel = SiteSearchViewModel(remote, movies(emptyList()), SavedStateHandle())
        viewModel.initialize("matrix", AppConstants.SearchType.TITLE, SearchSource.ALL)
        viewModel.setFilters(SearchFilters(genres = listOf("Аниме", "Мультфильм")))
        advanceUntilIdle()

        assertEquals(0, remoteCalls, "active filters must not query the site")
        assertTrue(viewModel.uiState.value.hits.isEmpty())
        assertTrue(viewModel.uiState.value.isEmpty, "empty result must render empty state")
    }

    @Test
    fun `active filters keep the catalog hits and report filtered source`() = scope.runTest {
        val remote = object : JsoupUpdateInteractor by mock() {
            override suspend fun searchOnSite(query: String, page: Int, searchType: String): DataResult<List<SiteSearchItem>> =
                DataResult.Success(emptyList())
        }
        val viewModel = SiteSearchViewModel(remote, movies(listOf(ViewMovie(dbId = 7, title = "Matrix"))), SavedStateHandle())
        viewModel.initialize("matrix", AppConstants.SearchType.TITLE, SearchSource.ALL)
        viewModel.setFilters(SearchFilters(genres = listOf("Фантастика")))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.hits.size)
        assertTrue(viewModel.uiState.value.filtersNotAppliedToSite)
    }

    /** За последней страницей сайт повторяет её же — полная страница тоже должна остановить обход. */
    @Test
    fun `repeated full site page stops paging`() = scope.runTest {
        val repeated = remotePage(1)
        val remote = object : JsoupUpdateInteractor by mock() {
            override suspend fun searchOnSite(query: String, page: Int, searchType: String) =
                DataResult.Success(repeated)
        }
        val viewModel = SiteSearchViewModel(remote, movies(emptyList()), SavedStateHandle())
        viewModel.initialize("matrix", AppConstants.SearchType.TITLE, SearchSource.REMOTE)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasMoreFilms)
        viewModel.loadMore()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.hasMoreFilms)
        assertEquals(10, viewModel.uiState.value.hits.size)
        assertEquals(2, viewModel.uiState.value.filmsPage)
    }

    private fun remotePage(start: Int) = (start until start + 10).map {
        SiteSearchItem(title = "Matrix $it", pageUrl = "/films/$it", type = MovieType.CINEMA, year = 2000)
    }

    @Test fun `catalog dedup does not hide repeated remote URLs from paging`() = scope.runTest {
        val local = (1..20).map {
            ViewMovie(dbId = it.toLong(), title = "Matrix $it", type = MovieType.CINEMA.value, year = 2000)
        }
        val remote = object : JsoupUpdateInteractor by mock() {
            override suspend fun searchOnSite(query: String, page: Int, searchType: String) =
                DataResult.Success(remotePage(if (page == 1) 1 else 11))
        }
        val viewModel = SiteSearchViewModel(remote, movies(local), SavedStateHandle())
        viewModel.initialize("matrix", AppConstants.SearchType.TITLE, SearchSource.ALL)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasMoreFilms)
        viewModel.loadMore()
        advanceUntilIdle()
        // A genuinely new page can consist entirely of catalog duplicates: keep paging.
        assertEquals(20, viewModel.uiState.value.hits.size)
        assertTrue(viewModel.uiState.value.hasMoreFilms)
        viewModel.loadMore()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.hasMoreFilms)
        assertEquals(3, viewModel.uiState.value.filmsPage)
    }

    @Test fun `displayed search mode stays with submitted results while editing the next mode`() = scope.runTest {
        val remote = object : JsoupUpdateInteractor by mock() {
            override suspend fun searchOnSite(query: String, page: Int, searchType: String) =
                DataResult.Success(remotePage(1))
        }
        val viewModel = SiteSearchViewModel(remote, movies(emptyList()), SavedStateHandle())
        viewModel.initialize("Smith", AppConstants.SearchType.ACTORS, SearchSource.REMOTE)
        advanceUntilIdle()
        viewModel.setSearchType(AppConstants.SearchType.TITLE)
        assertEquals(AppConstants.SearchType.ACTORS, viewModel.uiState.value.executedSearchType)
        viewModel.search()
        advanceUntilIdle()
        assertEquals(AppConstants.SearchType.TITLE, viewModel.uiState.value.executedSearchType)
    }
}
