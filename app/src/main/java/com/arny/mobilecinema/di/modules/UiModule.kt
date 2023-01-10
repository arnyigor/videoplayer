package com.arny.mobilecinema.di.modules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.arny.mobilecinema.di.AppViewModelFactory
import com.arny.mobilecinema.di.scopes.ViewModelKey
import com.arny.mobilecinema.domain.interactors.MainInteractor
import com.arny.mobilecinema.domain.interactors.MobileCinemaInteractor
import com.arny.mobilecinema.presentation.details.DetailsViewModel
import com.arny.mobilecinema.presentation.history.HistoryViewModel
import com.arny.mobilecinema.presentation.home.HomeViewModel
import com.arny.mobilecinema.presentation.playerview.PlayerViewModel
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import javax.inject.Provider

@Module(includes = [ActivitiesModule::class])
class UiModule {

    @Provides
    fun provideViewModelFactory(
        providers: Map<Class<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>
    ): ViewModelProvider.Factory = AppViewModelFactory(providers)

    @Provides
    @IntoMap
    @ViewModelKey(HomeViewModel::class)
    fun provideHomeViewModel(
        mainInteractor: MainInteractor,
        interactor: MobileCinemaInteractor,
    ): ViewModel = HomeViewModel(mainInteractor, interactor)

    @Provides
    @IntoMap
    @ViewModelKey(DetailsViewModel::class)
    fun provideDetailsViewModel(
        interactor: MobileCinemaInteractor,
    ): ViewModel = DetailsViewModel(interactor)

    @Provides
    @IntoMap
    @ViewModelKey(HistoryViewModel::class)
    fun provideHistoryViewModel(
        interactor: MobileCinemaInteractor,
    ): ViewModel = HistoryViewModel(interactor)
}