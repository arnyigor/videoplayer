package com.arny.mobilecinema.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arny.mobilecinema.data.api.ApiService
import com.arny.mobilecinema.data.api.KtorClient
import com.arny.mobilecinema.data.db.AppDatabase
import com.arny.mobilecinema.data.db.daos.FavoritesDao
import com.arny.mobilecinema.data.db.daos.HistoryDao
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.network.jsoup.JsoupService
import com.arny.mobilecinema.data.network.jsoup.JsoupServiceHelper
import com.arny.mobilecinema.data.repository.MoviesRepositoryImpl
import com.arny.mobilecinema.data.repository.jsoupupdate.JsoupUpdateRepositoryImpl
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.repository.resources.AppResourcesProvider
import com.arny.mobilecinema.data.repository.resources.AppResourcesProviderImpl
import com.arny.mobilecinema.data.repository.update.UpdateRepositoryImpl
import com.arny.mobilecinema.domain.repository.JsoupUpdateRepository
import com.arny.mobilecinema.domain.repository.MoviesRepository
import com.arny.mobilecinema.domain.repository.UpdateRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
interface DataModule {

    companion object {
        @Provides
        @Singleton
        fun providePreferences(context: Context): Prefs = Prefs.getInstance(context)

        @Provides
        @Singleton
        fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @Singleton
        fun provideKtorClient(): KtorClient = KtorClient()

        @Provides
        @Singleton
        fun provideApiService(ktor: KtorClient): ApiService = ApiService(ktor.client)

        @Provides
        @Singleton
        fun provideJsoupService(): JsoupService = JsoupService.getInstance()

        @Provides
        @Singleton
        fun provideJsoupHelperService(): JsoupServiceHelper = JsoupServiceHelper.getInstance()

        @Provides
        @Singleton
        fun provideDb(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            AppDatabase.DBNAME
        )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    db.execSQL("PRAGMA encoding='UTF-8';")
                }
            })
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
            )
            .build()

        @Provides
        @Singleton
        fun provideMoviesDao(db: AppDatabase): MovieDao = db.movieDao()

        @Provides
        @Singleton
        fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()

        @Provides
        @Singleton
        fun provideFavoritesDao(db: AppDatabase): FavoritesDao = db.favoritesDao()

    }

    @Binds
    @Singleton
    fun bindsDataRepository(impl: UpdateRepositoryImpl): UpdateRepository

    @Binds
    @Singleton
    fun bindsMoviesRepository(impl: MoviesRepositoryImpl): MoviesRepository

    @Binds
    @Singleton
    fun bindsJsoupUpdateRepository(impl: JsoupUpdateRepositoryImpl): JsoupUpdateRepository

    @Binds
    @Singleton
    fun bindsAppResourcesProvider(impl: AppResourcesProviderImpl): AppResourcesProvider
}