package com.arny.homecinema.data.di

import android.content.Context
import com.arny.homecinema.data.repository.VideoRepository
import com.arny.homecinema.data.repository.VideoRepositoryImpl
import com.arny.homecinema.data.repository.sources.AssetsReader
import com.arny.homecinema.data.repository.sources.AssetsReaderImpl
import com.arny.homecinema.data.repository.sources.Prefs
import dagger.Binds
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
interface DataModule {
    @Binds
    @Singleton
    fun bindsVideoRepository(repository: VideoRepositoryImpl): VideoRepository

    @Binds
    @Singleton
    fun bindsAssetsReader(assetsReader: AssetsReaderImpl): AssetsReader

    companion object {
        @Provides
        @Singleton
        fun providePreferences(context: Context): Prefs {
            return Prefs.getInstance(context)
        }
    }
}