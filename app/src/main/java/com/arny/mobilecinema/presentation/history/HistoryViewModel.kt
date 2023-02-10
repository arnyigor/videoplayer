package com.arny.mobilecinema.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.arny.mobilecinema.domain.interactors.MoviesInteractor
import com.arny.mobilecinema.domain.models.ViewMovie
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class,FlowPreview::class)
class HistoryViewModel @Inject constructor(
    private val interactor: MoviesInteractor
) : ViewModel() {
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val actionStateFlow = MutableSharedFlow<String>()
    var historyDataFlow: Flow<PagingData<ViewMovie>> = actionStateFlow
        .onStart { emit("") }
        .flatMapLatest { interactor.getHistoryMovies(search = it) }
        .distinctUntilChanged()
        .cachedIn(viewModelScope)

    fun loadHistory(search: String = "") {
        viewModelScope.launch {
            actionStateFlow.emit(search)
        }
    }
}