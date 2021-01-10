package com.arny.homecinema.presentation.history

import com.arny.homecinema.data.models.DataResult
import com.arny.homecinema.di.models.Movie
import moxy.MvpView
import moxy.viewstate.strategy.alias.AddToEndSingle

@AddToEndSingle
interface HistoryView : MvpView {
    fun showError(result: DataResult<Throwable>)
    fun updateList(result: DataResult<List<Movie>>)
}
