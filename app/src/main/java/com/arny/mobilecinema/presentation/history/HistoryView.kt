package com.arny.mobilecinema.presentation.history

import androidx.annotation.StringRes
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.di.models.Movie
import moxy.MvpView
import moxy.viewstate.strategy.alias.AddToEndSingle
import moxy.viewstate.strategy.alias.OneExecution

@AddToEndSingle
interface HistoryView : MvpView {
    fun showError(result: DataResult<Throwable>)
    fun updateList(result: DataResult<List<Movie>>)
    @OneExecution
    fun toastMessage(@StringRes strRes: Int?)
}
