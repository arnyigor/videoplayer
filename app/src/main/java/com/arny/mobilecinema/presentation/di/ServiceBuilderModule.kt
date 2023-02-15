package com.arny.mobilecinema.presentation.di

import com.arny.mobilecinema.presentation.player.MovieDownloadService
import com.arny.mobilecinema.presentation.update.UpdateService
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
internal abstract class ServiceBuilderModule {
    @ContributesAndroidInjector
    abstract fun contributeUpdateService(): UpdateService

    @ContributesAndroidInjector
    abstract fun contributeMovieDownloadService(): MovieDownloadService
}