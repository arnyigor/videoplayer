package com.arny.mobilecinema.data.di

import android.content.Context
import com.arny.mobilecinema.data.repository.VideoRepositoryImpl
import com.arny.mobilecinema.data.repository.sources.assets.AssetsReader
import com.arny.mobilecinema.data.repository.sources.assets.AssetsReaderImpl
import com.arny.mobilecinema.data.repository.sources.cache.VideoCache
import com.arny.mobilecinema.data.repository.sources.cache.VideoCacheImpl
import com.arny.mobilecinema.data.repository.sources.prefs.Prefs
import com.arny.mobilecinema.data.repository.sources.store.StoreProvider
import com.arny.mobilecinema.data.repository.sources.store.StoreProviderImpl
import com.arny.mobilecinema.domain.repository.VideoRepository
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