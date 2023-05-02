package com.arny.mobilecinema.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.MoviesInteractor
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.presentation.uimodels.Alert
import com.arny.mobilecinema.presentation.uimodels.AlertType
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ResourceString
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
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
    private val _alert = MutableSharedFlow<Alert>()
    val alert = _alert.asSharedFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _order = MutableStateFlow("")
    val order = _order.asStateFlow()
    private var search = UiAction.Search()
    private val actionStateFlow = MutableSharedFlow<UiAction>()
    var moviesDataFlow: Flow<PagingData<ViewMovie>> = actionStateFlow
        .filterIsInstance<UiAction.Search>()
        .distinctUntilChanged()
        .debounce(350)
        .onStart {
            val savedOrder = moviesInteractor.getOrder()
            _order.value = savedOrder
            emit(UiAction.Search(order = savedOrder))
        }
        .flatMapLatest { search ->
            this.search = search
            moviesInteractor.getMovies(
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
                            if (updateTime.isNotBlank()) {
                                _alert.emit(
                                    Alert(
                                        title = ResourceString(R.string.new_films_update),
                                        content = ResourceString(
                                            R.string.question_update_format,
                                            updateTime
                                        ),
                                        btnOk = ResourceString(android.R.string.ok),
                                        btnCancel = ResourceString(android.R.string.cancel),
                                        type = AlertType.Update
                                    )
                                )
                            }
                        }
                    }
                }
        }
    }

    fun loadMovies(search: String = "") {
        viewModelScope.launch {
            actionStateFlow.emit(
                this@HomeViewModel.search.copy(
                    query = search,
                    order = _order.value
                )
            )
        }
    }

    fun onConfirmAlert(type: AlertType) {
        viewModelScope.launch {
            when (type) {
                AlertType.Update -> {
                    _toast.emit(ResourceString(R.string.update_started))
                    dataUpdateInteractor.requestFile()
                }
            }
        }
    }

    fun onCancelAlert(type: AlertType) {
        viewModelScope.launch {
            when (type) {
                AlertType.Update -> {
                    dataUpdateInteractor.resetUpdate()
                }
            }
        }
    }

    fun setOrder(order: String) {
        viewModelScope.launch {
            moviesInteractor.saveOrder(order)
            actionStateFlow.emit(
                search.copy(
                    order = order
                )
            )
        }
    }

    fun setSearchType(searchType: String) {
        viewModelScope.launch {
            actionStateFlow.emit(
                search.copy(
                    searchType = searchType
                )
            )
        }
    }
}