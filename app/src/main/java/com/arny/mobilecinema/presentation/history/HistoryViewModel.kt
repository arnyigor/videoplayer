package com.arny.mobilecinema.presentation.history

import androidx.lifecycle.ViewModel
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.MoviesInteractor
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class HistoryViewModel @Inject constructor(
    private val interactor: MoviesInteractor
) : ViewModel() {
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _mainContent = MutableSharedFlow<DataResult<List<Movie>>>()
    val mainContent = _mainContent.asSharedFlow()
    private val _toast = MutableSharedFlow<IWrappedString>()
    val toast = _toast.asSharedFlow()

    private suspend fun setError(throwable: Throwable) {
        _mainContent.emit(DataResult.Error(throwable))
    }
}