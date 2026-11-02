package com.arny.mobilecinema.presentation.sitesearch

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
                genre: String?,
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
                genre: String?,
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
                query: String, kinds: List<MovieType>, genre: String?, country: String?,
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

}
