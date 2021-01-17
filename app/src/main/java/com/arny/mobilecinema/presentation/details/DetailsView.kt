package com.arny.mobilecinema.presentation.details

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.di.models.Movie
import moxy.MvpView
import moxy.viewstate.strategy.alias.AddToEndSingle
import moxy.viewstate.strategy.alias.OneExecution

@AddToEndSingle
interface DetailsView : MvpView {
    @OneExecution
    fun showError(result: DataResult<Throwable>)
    fun showLoading(show: Boolean)
    @OneExecution
    fun showVideo(data: DataResult<Movie>?)
}