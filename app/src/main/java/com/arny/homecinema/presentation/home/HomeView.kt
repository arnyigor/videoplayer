package com.arny.homecinema.presentation.home

import com.arny.homecinema.data.models.DataResult
import com.arny.homecinema.di.models.MainPageContent
import moxy.MvpView
import moxy.viewstate.strategy.alias.AddToEndSingle
import moxy.viewstate.strategy.alias.OneExecution

@AddToEndSingle
interface HomeView : MvpView {
    fun showMainContent(result: DataResult<MainPageContent>)
    fun showMainContentError(error: DataResult<MainPageContent>)
    fun showLoading(show: Boolean)
    @OneExecution
    fun chooseHost(hostsResult: DataResult<Pair<Array<String>, Int>>)
}
