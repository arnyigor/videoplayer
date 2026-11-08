package com.arny.mobilecinema.presentation.tv.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.interactors.history.HistoryInteractor
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor

class TvLibraryViewModel(
    movies: MoviesInteractor,
    history: HistoryInteractor,
    private val saved: SavedStateHandle,
) : ViewModel() {
    val isHistory = saved.get<String>("kind") == "HISTORY"
    val items = (if (isHistory) history.getHistoryMovies(order = AppConstants.Order.LAST_TIME, searchType = "")
        else movies.getFavoriteMovies("", AppConstants.Order.LAST_TIME, "")).cachedIn(viewModelScope)
    val focusedId: Long? get() = saved["focused_id"]
    val focusedPosition: Int get() = saved["focused_position"] ?: 0
    fun focus(id: Long, position: Int) {
        saved["focused_id"] = id
        saved["focused_position"] = position
    }
}
