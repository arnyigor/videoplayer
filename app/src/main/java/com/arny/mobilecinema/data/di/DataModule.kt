package com.arny.mobilecinema.data.di

import android.content.Context
import androidx.room.Room
import com.arny.mobilecinema.data.api.ApiService
import com.arny.mobilecinema.data.api.KtorClient
import com.arny.mobilecinema.data.db.AppDatabase
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.network.YouTubeVideoInfoRetriever
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.repository.update.UpdateRepositoryImpl
import com.arny.mobilecinema.data.utils.MegaHandler
import com.arny.mobilecinema.domain.repository.UpdateRepository
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
        fun provideMegaHandler(): MegaHandler = MegaHandler()

        @Provides
        @Singleton
        fun provideKtorClient(): KtorClient = KtorClient()

        @Provides
        @Singleton
        fun provideApiService(ktor: KtorClient): ApiService = ApiService(ktor.client)

        @Provides
        @Singleton
        fun provideDb(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            AppDatabase.DBNAME
        ).build()

        @Provides
        @Singleton
        fun provideMoviesDao(db: AppDatabase): MovieDao = db.movieDao()

        @Provides
        @Singleton
        fun provideYouTubeVideoInfoRetriever(ktorClient: KtorClient) =
            YouTubeVideoInfoRetriever(ktorClient.client)
    }

    @Binds
    @Singleton
    fun bindsDataRepository(impl: UpdateRepositoryImpl): UpdateRepository
}