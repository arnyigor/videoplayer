package com.arny.mobilecinema.domain.di

import android.content.Context
import com.arny.mobilecinema.data.network.YouTubeVideoInfoRetriever
import com.arny.mobilecinema.domain.interactors.history.HistoryInteractor
import com.arny.mobilecinema.domain.interactors.history.HistoryInteractorImpl
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractorImpl
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractorImpl
import com.arny.mobilecinema.domain.repository.UpdateRepository
import com.arny.mobilecinema.presentation.player.PlayerSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

@Module
interface DomainModule {
    @Binds
    @Singleton
    fun bindMoviesInteractor(impl: MoviesInteractorImpl): MoviesInteractor

    @Binds
    @Singleton
    fun bindHistoryInteractor(impl: HistoryInteractorImpl): HistoryInteractor

    @Binds
    @Singleton
    fun bindUpdateInteractor(impl: DataUpdateInteractorImpl): DataUpdateInteractor


    companion object {
        @Singleton
        @Provides
        fun providePlayerSource(
            context: Context,
            retriever: YouTubeVideoInfoRetriever,
            updateRepository: UpdateRepository,
            dispatcher: CoroutineDispatcher
        ) = PlayerSource(context, updateRepository, retriever, dispatcher)
    }
}
