package com.arny.mobilecinema.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.presentation.uimodels.Alert
import com.arny.mobilecinema.presentation.uimodels.AlertType
import com.arny.mobilecinema.presentation.utils.BufferedChannel
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ResourceString
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomeViewModel @Inject constructor(
    private val dataUpdateInteractor: DataUpdateInteractor,
    private val moviesInteractor: MoviesInteractor,
) : ViewModel() {
    private val _error = MutableSharedFlow<IWrappedString>()
    val error = _error.asSharedFlow()
    private val _empty = MutableStateFlow(false)
    val empty = _empty.asStateFlow()
    private val _toast = MutableSharedFlow<IWrappedString>()
    val toast = _toast.asSharedFlow()
    private val _alert = BufferedChannel<Alert>()
    val alert = _alert.receiveAsFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _order = MutableStateFlow("")
    val order = _order.asStateFlow()
    private var search = UiAction.Search()
    private var query = ""
    private var searchType = ""
    private var searchAddTypes = listOf(
        AppConstants.SearchType.CINEMA,
        AppConstants.SearchType.SERIAL,
    )
    private val actionStateFlow = MutableSharedFlow<UiAction>()
    val updateText = dataUpdateInteractor.updateTextFlow
    var moviesDataFlow: Flow<PagingData<ViewMovie>> = actionStateFlow
        .filterIsInstance<UiAction.Search>()
        .distinctUntilChanged()
        .debounce(350)
        .onStart {
            val savedOrder = moviesInteractor.getOrder()
            _order.value = savedOrder
            emit(
                UiAction.Search(
                    order = savedOrder,
                    searchType = searchType,
                    searchAddTypes = searchAddTypes
                )
            )
        }
        .flatMapLatest { search ->
            this.search = search
            moviesInteractor.getMovies(
                search = search.query,
                order = search.order,
                searchType = search.searchType,
                searchAddTypes = search.searchAddTypes
            )
        }
        .onEach {
            _loading.value = false
            checkEmpty()
        }
        .cachedIn(viewModelScope)

    private fun checkEmpty() {
        viewModelScope.launch {
            moviesInteractor.isMoviesEmpty()
                .collectLatest { data ->
                    when (data) {
                        is DataResult.Error -> {}
                        is DataResult.Success -> _empty.value = data.result
                    }
                }
        }
    }

    fun downloadData(force: Boolean) {
        viewModelScope.launch {
            dataUpdateInteractor.getUpdateDate(force)
                .onStart { _loading.value = true }
                .onCompletion { _loading.value = false }
                .collect { result ->
                    when (result) {
                        is DataResult.Error -> {
                            result.throwable.printStackTrace()
                        }

                        is DataResult.Success -> {
                            val updateTime = result.result
                            if (updateTime.isNotBlank() && !updateTime.contains("""[/|\\]""".toRegex())) {
                                _alert.trySend(
                                    Alert(
                                        title = ResourceString(R.string.new_films_update),
                                        content = ResourceString(
                                            R.string.question_update_format,
                                            updateTime
                                        ),
                                        btnOk = ResourceString(android.R.string.ok),
                                        btnCancel = ResourceString(android.R.string.cancel),
                                        type = AlertType.Update(false)
                                    )
                                )
                            }
                        }
                    }
                }
        }
    }

    fun loadMovies(query: String = "", submit: Boolean = true, delay: Boolean = false) {
        viewModelScope.launch {
            this@HomeViewModel.query = query
            if (submit) {
                if (delay) {
                    delay(350)
                }
                actionStateFlow.emit(UiAction.Search())
                actionStateFlow.emit(
                    UiAction.Search(
                        searchType = searchType,
                        query = query,
                        order = _order.value,
                        searchAddTypes = query.takeIf { it.isNotBlank() }?.let { searchAddTypes } ?:emptyList()
                    )
                )
            }
        }
    }

    fun onConfirmAlert(type: AlertType) {
        viewModelScope.launch {
            when (type) {
                is AlertType.Update -> {
                    _toast.emit(ResourceString(R.string.update_started))
                    dataUpdateInteractor.requestFile(type.force)
                }
                else -> {}
            }
        }
    }

    fun onCancelAlert(type: AlertType) {
        viewModelScope.launch {
            when (type) {
                is AlertType.Update -> {
                    dataUpdateInteractor.resetUpdate()
                }

                else -> {}
            }
        }
    }

    fun onNeutralAlert(type: AlertType) {
        viewModelScope.launch {
            when (type) {
                is AlertType.Update -> {
                    _alert.trySend(
                        Alert(
                            title = ResourceString(R.string.full_update_title),
                            content = ResourceString(R.string.full_update_description),
                            btnOk = ResourceString(android.R.string.ok),
                            btnCancel = ResourceString(android.R.string.cancel),
                            type = AlertType.Update(true)
                        )
                    )
                }

                else -> {}
            }
        }
    }

    fun setOrder(order: String) {
        viewModelScope.launch {
            moviesInteractor.saveOrder(order)
            actionStateFlow.emit(
                UiAction.Search(
                    searchType = searchType,
                    query = search.query,
                    order = order,
                    searchAddTypes = searchAddTypes
                )
            )
        }
    }

    fun setSearchType(type: String, submit: Boolean = true, addTypes: List<String>) {
        viewModelScope.launch {
            searchType = type
            searchAddTypes = addTypes
            if (submit) {
                actionStateFlow.emit(UiAction.Search())
                actionStateFlow.emit(
                    UiAction.Search(
                        searchType = searchType,
                        query = search.query,
                        order = _order.value,
                        searchAddTypes = searchAddTypes
                    )
                )
            }
        }
    }
}