package com.arny.mobilecinema.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.repository.playback.PlaybackProgressStore
import com.arny.mobilecinema.domain.interactors.history.HistoryInteractor
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.domain.playback.PlaybackProgress
import com.arny.mobilecinema.presentation.home.UiAction
import com.arny.mobilecinema.presentation.uimodels.ListScreenState
import com.arny.mobilecinema.presentation.utils.BufferedChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/** Одноразовые действия экрана истории. */
sealed interface HistoryCommand {
    /** Продолжить просмотр: плеер сам возьмёт серию и позицию из общего стора. */
    data class Play(val movie: Movie) : HistoryCommand
    data object PlayError : HistoryCommand
}

@OptIn(ExperimentalCoroutinesApi::class,FlowPreview::class)
class HistoryViewModel constructor(
    private val interactor: MoviesInteractor,
    private val historyInteractor: HistoryInteractor,
    private val progressStore: PlaybackProgressStore,
) : ViewModel() {
    private val _screenState = MutableStateFlow<ListScreenState>(ListScreenState.Loading)
    val screenState = _screenState.asStateFlow()

    /**
     * Прогресс по тайтлам для строк истории (HIST-01).
     *
     * Отдельный поток, а не запрос на каждую строку: один подписчик — вся пагинация
     * обходится без N+1 запросов.
     */
    val progressByMovie = progressStore.observeProgressByMovie()
        .catch { e ->
            Timber.e(e, "history: failed to observe playback progress")
            emit(emptyMap())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyMap<Long, PlaybackProgress>(),
        )

    private val _commands = BufferedChannel<HistoryCommand>()
    val commands = _commands.receiveAsFlow()

    private val _order = MutableStateFlow("")
    val order = _order.asStateFlow()
    private var search = UiAction.Search()
    private var started = false
    private var query = ""
    private var searchType = ""
    private val actionStateFlow = MutableSharedFlow<UiAction>()
    var historyDataFlow: Flow<PagingData<ViewMovie>> = actionStateFlow
        .filterIsInstance<UiAction.Search>()
        .distinctUntilChanged()
        .debounce(350)
        .onStart {
            started = true
            _screenState.value = ListScreenState.Loading
            val savedOrder = interactor.getOrder(true)
            _order.value = savedOrder
            emit(
                UiAction.Search(
                    order = savedOrder,
                    searchType = searchType,
                )
            )
        }
        .flatMapLatest { search ->
            this.search = search
            historyInteractor.getHistoryMovies(
                search = search.query,
                order = search.order,
                searchType = search.searchType,
            )
        }
        .onEach { pagingData ->
            // Empty state will be determined by LoadStateListener in Fragment
            // based on actual adapter itemCount, so we just set Content here
            _screenState.value = ListScreenState.Content
        }
        .cachedIn(viewModelScope)

    fun loadHistory(query: String = "", submit: Boolean = true, delay: Boolean = false) {
        viewModelScope.launch {
            this@HistoryViewModel.query = query
            if (submit) {
                if (delay) {
                    delay(350)
                }
                actionStateFlow.emit(
                    UiAction.Search(
                        searchType = searchType,
                        query = query,
                        order = _order.value
                    )
                )
            }
        }
    }


    fun setOrder(order: String) {
        viewModelScope.launch {
            interactor.saveHistoryOrder(order)
            actionStateFlow.emit(
                UiAction.Search(
                    searchType = searchType,
                    query = search.query,
                    order = order,
                )
            )
        }
    }

    fun setSearchType(type: String, submit: Boolean = true) {
        viewModelScope.launch {
            searchType = type
            if (submit) {
                actionStateFlow.emit(
                    UiAction.Search(
                        searchType = searchType,
                        query = search.query,
                        order = _order.value,
                    )
                )
            }
        }
    }

    fun clearAllViewHistory() {
        viewModelScope.launch {
            historyInteractor.clearAllViewHistory()
                .collectLatest { data ->
                    when (data) {
                        is DataResult.Error -> {}
                        is DataResult.Success -> {
                            _screenState.value = ListScreenState.Empty
                            reloadHistory()
                        }
                    }
                }
        }
    }

    fun reloadHistory() {
        if (started) {
            viewModelScope.launch {
                actionStateFlow.emit(
                    UiAction.Search(
                        searchType = searchType,
                        query = query,
                        order = _order.value,
                        triggerId = System.currentTimeMillis() // Гарантированно новый хэш объекта
                    )
                )
            }
        }
    }

    /**
     * «Продолжить» из строки истории: нужен полный [Movie], чтобы плеер поднял источники.
     * Серию и позицию плеер берёт из общего стора прогресса — так же, как с экрана деталей.
     */
    fun playFromHistory(movieDbId: Long) {
        viewModelScope.launch {
            val result = interactor.getMovie(movieDbId)
                .catch { e ->
                    Timber.e(e, "history: failed to load movie %d", movieDbId)
                    emit(DataResult.Error(e))
                }
                .first()
            when (result) {
                is DataResult.Success -> _commands.send(HistoryCommand.Play(result.result))
                is DataResult.Error -> {
                    Timber.w(result.throwable, "history: movie %d is not playable", movieDbId)
                    _commands.send(HistoryCommand.PlayError)
                }
            }
        }
    }
}