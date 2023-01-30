package com.arny.mobilecinema.data.di

import android.content.Context
import com.arny.mobilecinema.data.api.ApiService
import com.arny.mobilecinema.data.api.JsoupService
import com.arny.mobilecinema.data.api.KtorClient
import com.arny.mobilecinema.data.repository.MegaRepositoryImpl
import com.arny.mobilecinema.data.repository.VideoRepositoryImpl
import com.arny.mobilecinema.data.repository.gists.GistsRepositoryImpl
import com.arny.mobilecinema.data.repository.sources.assets.AssetsReader
import com.arny.mobilecinema.data.repository.sources.assets.AssetsReaderImpl
import com.arny.mobilecinema.data.repository.sources.cache.VideoCache
import com.arny.mobilecinema.data.repository.sources.cache.VideoCacheImpl
import com.arny.mobilecinema.data.repository.sources.jsoup.JsoupRepositoryImpl
import com.arny.mobilecinema.data.repository.sources.prefs.Prefs
import com.arny.mobilecinema.data.repository.sources.store.StoreProvider
import com.arny.mobilecinema.data.repository.sources.store.StoreProviderImpl
import com.arny.mobilecinema.data.utils.MegaHandler
import com.arny.mobilecinema.domain.repository.GistsRepository
import com.arny.mobilecinema.domain.repository.JsoupRepository
import com.arny.mobilecinema.domain.repository.MegaRepository
import com.arny.mobilecinema.domain.repository.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
interface DataModule {

    companion object {
        @Provides
        @Singleton
        fun providePreferences(context: Context): Prefs = Prefs.getInstance(context)

        @Provides
        @Singleton
        fun provideJsoupService(): JsoupService = JsoupService.getInstance()

        @Provides
        @Singleton
        fun provideMegaHandler(): MegaHandler = MegaHandler()

        @Provides
        @Singleton
        fun provideKtorClient(): KtorClient = KtorClient()

        @Provides
        @Singleton
        fun provideApiService(ktor: KtorClient): ApiService = ApiService(ktor.client)
    }

    @Binds
    @Singleton
    fun bindsVideoRepository(repository: VideoRepositoryImpl): VideoRepository

    @Binds
    @Singleton
    fun bindsMegaRepository(impl: MegaRepositoryImpl): MegaRepository

    @Binds
    @Singleton
    fun bindsAssetsReader(assetsReader: AssetsReaderImpl): AssetsReader

    @Binds
    @Singleton
    fun bindsStoreProvier(storeProvider: StoreProviderImpl): StoreProvider

    @Binds
    @Singleton
    fun bindsVideoCache(cache: VideoCacheImpl): VideoCache

    @Binds
    @Singleton
    fun bindsGistsRepository(impl: GistsRepositoryImpl): GistsRepository

    @Binds
    @Singleton
    fun bindsJsoupRepository(impl: JsoupRepositoryImpl): JsoupRepository
}