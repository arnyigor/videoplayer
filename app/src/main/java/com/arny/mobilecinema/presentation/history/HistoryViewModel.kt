package com.arny.mobilecinema.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.MoviesInteractor
import com.arny.mobilecinema.domain.models.ViewMovie
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
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
    private val actionStateFlow = MutableSharedFlow<String>()
    var historyDataFlow: Flow<PagingData<ViewMovie>> = actionStateFlow
        .onStart {
            _loading.value = true
            emit("")
        }
        .flatMapLatest { interactor.getHistoryMovies(search = it) }
        .distinctUntilChanged()
        .onEach {
            _loading.value = false
            checkEmpty()
        }
        .cachedIn(viewModelScope)

    fun loadHistory(search: String = "") {
        viewModelScope.launch {
            actionStateFlow.emit(search)
        }
    }

    private fun checkEmpty() {
        viewModelScope.launch {
            interactor.isHistoryEmpty()
                .collectLatest { data ->
                    println("checkEmpty:$data")
                    when (data) {
                        is DataResult.Error -> {}
                        is DataResult.Success -> _empty.value = data.result
                    }
                }
        }
    }
}