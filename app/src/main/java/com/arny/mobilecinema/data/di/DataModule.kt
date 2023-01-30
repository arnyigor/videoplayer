package com.arny.mobilecinema.data.di

import android.content.Context
import com.arny.mobilecinema.data.api.ApiService
import com.arny.mobilecinema.data.api.JsoupService
import com.arny.mobilecinema.data.api.KtorClient
import com.arny.mobilecinema.data.network.YouTubeVideoInfoRetriever
import com.arny.mobilecinema.data.repository.DataRepositoryImpl
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.utils.MegaHandler
import com.arny.mobilecinema.domain.repository.DataRepository
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

        @Provides
        @Singleton
        fun provideYouTubeVideoInfoRetriever(ktorClient: KtorClient) =
            YouTubeVideoInfoRetriever(ktorClient.client)
    }

    @Binds
    @Singleton
    fun bindsDataRepository(impl: DataRepositoryImpl): DataRepository
}