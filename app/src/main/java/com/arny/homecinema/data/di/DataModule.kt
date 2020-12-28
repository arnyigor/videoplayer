package com.arny.homecinema.data.di

import android.content.Context
import com.arny.homecinema.data.repository.VideoRepository
import com.arny.homecinema.data.repository.VideoRepositoryImpl
import com.arny.homecinema.data.repository.sources.assets.AssetsReader
import com.arny.homecinema.data.repository.sources.assets.AssetsReaderImpl
import com.arny.homecinema.data.repository.sources.cache.VideoCache
import com.arny.homecinema.data.repository.sources.cache.VideoCacheImpl
import com.arny.homecinema.data.repository.sources.prefs.Prefs
import com.arny.homecinema.data.repository.sources.store.StoreProvider
import com.arny.homecinema.data.repository.sources.store.StoreProviderImpl
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

    @Binds
    @Singleton
    fun bindsStoreProvier(storeProvider: StoreProviderImpl): StoreProvider

    @Binds
    @Singleton
    fun bindsVideoCache(cache: VideoCacheImpl): VideoCache

    companion object {
        @Provides
        @Singleton
        fun providePreferences(context: Context): Prefs {
            return Prefs.getInstance(context)
        }
    }
}