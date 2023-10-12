package com.arny.mobilecinema.di.modules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.arny.mobilecinema.di.AppViewModelFactory
import com.arny.mobilecinema.di.scopes.ViewModelKey
import com.arny.mobilecinema.domain.interactors.history.HistoryInteractor
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
import com.arny.mobilecinema.presentation.details.DetailsViewModel
import com.arny.mobilecinema.presentation.extendedsearch.ExtendedSearchViewModel
import com.arny.mobilecinema.presentation.history.HistoryViewModel
import com.arny.mobilecinema.presentation.home.HomeViewModel
import com.arny.mobilecinema.presentation.player.PlayerSource
import com.arny.mobilecinema.presentation.playerview.PlayerViewModel
import com.arny.mobilecinema.presentation.splash.SplashViewModel
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
        updateInteractor: DataUpdateInteractor,
        moviesInteractor: MoviesInteractor,
    ): ViewModel = HomeViewModel(updateInteractor, moviesInteractor)

    @Provides
    @IntoMap
    @ViewModelKey(DetailsViewModel::class)
    fun provideDetailsViewModel(
        interactor: MoviesInteractor,
        historyInteractor: HistoryInteractor,
        playerSource: PlayerSource
    ): ViewModel = DetailsViewModel(interactor, historyInteractor, playerSource)

    @Provides
    @IntoMap
    @ViewModelKey(HistoryViewModel::class)
    fun provideHistoryViewModel(
        interactor: MoviesInteractor,
        historyInteractor: HistoryInteractor,
    ): ViewModel = HistoryViewModel(interactor, historyInteractor)

    @Provides
    @IntoMap
    @ViewModelKey(PlayerViewModel::class)
    fun providePlayerViewModel(
        interactor: MoviesInteractor,
        historyInteractor: HistoryInteractor
    ): ViewModel = PlayerViewModel(interactor,historyInteractor)

    @Provides
    @IntoMap
    @ViewModelKey(SplashViewModel::class)
    fun provideSplashViewModel(
        interactor: DataUpdateInteractor,
    ): ViewModel = SplashViewModel(interactor)

    @Provides
    @IntoMap
    @ViewModelKey(ExtendedSearchViewModel::class)
    fun provideExtendedSearchViewModel(
        moviesInteractor: MoviesInteractor,
    ): ViewModel = ExtendedSearchViewModel(moviesInteractor)
}