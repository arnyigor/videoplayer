package com.arny.mobilecinema.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.MoviesInteractor
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.presentation.home.UiAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class,FlowPreview::class)
class HistoryViewModel @Inject constructor(
    private val interactor: MoviesInteractor
) : ViewModel() {
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _empty = MutableStateFlow(false)
    val empty = _empty.asStateFlow()
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
            val savedOrder = interactor.getOrder()
            _order.value = savedOrder
            emit(
                UiAction.Search(
                    order = savedOrder,
                    searchType = searchType
                )
            )
        }
        .flatMapLatest { search ->
            this.search = search
            interactor.getHistoryMovies(
                search = search.query,
                order = search.order,
                searchType = search.searchType
            )
        }
        .onEach {
            _loading.value = false
            checkEmpty()
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
            interactor.saveOrder(order)
            actionStateFlow.emit(
                UiAction.Search(
                    searchType = searchType,
                    query = search.query,
                    order = order
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
                        order = _order.value
                    )
                )
            }
        }
    }

    private fun checkEmpty() {
        viewModelScope.launch {
            interactor.isHistoryEmpty()
                .collectLatest { data ->
                    when (data) {
                        is DataResult.Error -> {}
                        is DataResult.Success -> _empty.value = data.result
                    }
                }
        }
    }

    fun clearAllViewHistory() {
        viewModelScope.launch {
            interactor.clearAllViewHistory()
                .collectLatest { data ->
                    when (data) {
                        is DataResult.Error -> {}
                        is DataResult.Success -> {
                            loadHistory("newsearch")
                            loadHistory()
                        }
                    }
                }
        }
    }

    fun reloadHistory() {
        if (started) {
            loadHistory("newsearch")
            loadHistory()
        }
    }
}