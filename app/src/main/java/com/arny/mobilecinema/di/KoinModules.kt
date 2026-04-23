package com.arny.mobilecinema.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arny.mobilecinema.data.api.ApiService
import com.arny.mobilecinema.data.api.KtorClient
import io.ktor.client.HttpClient
import com.arny.mobilecinema.data.db.AppDatabase
import com.arny.mobilecinema.data.feedback.FeedbackDatabaseImpl
import com.arny.mobilecinema.data.models.MovieMapper
import com.arny.mobilecinema.data.network.jsoup.JsoupService
import com.arny.mobilecinema.data.network.jsoup.JsoupServiceHelper
import com.arny.mobilecinema.data.repository.MoviesRepositoryImpl
import com.arny.mobilecinema.data.repository.jsoupupdate.JsoupUpdateRepositoryImpl
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.repository.resources.AppResourcesProvider
import com.arny.mobilecinema.data.repository.resources.AppResourcesProviderImpl
import com.arny.mobilecinema.data.repository.update.UpdateRepositoryImpl
import com.arny.mobilecinema.domain.interactors.feedback.FeedbackDatabase
import com.arny.mobilecinema.domain.interactors.feedback.FeedbackInteractor
import com.arny.mobilecinema.domain.interactors.feedback.FeedbackInteractorImpl
import com.arny.mobilecinema.domain.interactors.history.HistoryInteractor
import com.arny.mobilecinema.domain.interactors.history.HistoryInteractorImpl
import com.arny.mobilecinema.domain.interactors.jsoupupdate.JsoupUpdateInteractor
import com.arny.mobilecinema.domain.interactors.jsoupupdate.JsoupUpdateInteractorImpl
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractorImpl
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractorImpl
import com.arny.mobilecinema.domain.repository.JsoupUpdateRepository
import com.arny.mobilecinema.domain.repository.MoviesRepository
import com.arny.mobilecinema.domain.repository.UpdateRepository
import com.arny.mobilecinema.presentation.details.DetailsViewModel
import com.arny.mobilecinema.presentation.extendedsearch.ExtendedSearchViewModel
import com.arny.mobilecinema.presentation.favorite.FavoritesViewModel
import com.arny.mobilecinema.presentation.history.HistoryViewModel
import com.arny.mobilecinema.presentation.home.HomeViewModel
import com.arny.mobilecinema.presentation.player.PlayerSource
import com.arny.mobilecinema.presentation.playerview.PlayerViewModel
import com.arny.mobilecinema.presentation.splash.SplashViewModel
import com.arny.mobilecinema.presentation.tv.home.TvHomeViewModel
import com.arny.mobilecinema.presentation.tv.search.TvSearchViewModel
import com.arny.mobilecinema.presentation.tv.viewmodel.TvDetailsViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val dataModule = module {
    single { Prefs.getInstance(androidContext()) }
    single<CoroutineDispatcher> { Dispatchers.IO }
    single { KtorClient() }
    single<HttpClient> { get<KtorClient>().client }
    single { ApiService(get()) }
    single { JsoupService.getInstance() }
    single { JsoupServiceHelper.getInstance() }
    single { MovieMapper() }
    single {
        Room.databaseBuilder(
            androidContext(),
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
                AppDatabase.MIGRATION_4_5,
            )
            .build()
    }
    single { get<AppDatabase>().movieDao() }
    single { get<AppDatabase>().historyDao() }
    single { get<AppDatabase>().favoritesDao() }
    single<FeedbackDatabase> { FeedbackDatabaseImpl(get(),get()) }
    single<UpdateRepository> { UpdateRepositoryImpl(get(), get(), get(), androidContext(), get()) }
    single<MoviesRepository> { MoviesRepositoryImpl(get(), get(), get(), get(), get(), get()) }
    single<JsoupUpdateRepository> { JsoupUpdateRepositoryImpl(get(), get()) }
    single<AppResourcesProvider> { AppResourcesProviderImpl(androidContext()) }
    single { PlayerSource(androidContext(), get(), get<CoroutineDispatcher>()) }
}

val domainModule = module {
    single<MoviesInteractor> { MoviesInteractorImpl(get(), get(), get<CoroutineDispatcher>()) }
    single<HistoryInteractor> { HistoryInteractorImpl(get(), get<CoroutineDispatcher>(), get()) }
    single<DataUpdateInteractor> { DataUpdateInteractorImpl(get()) }
    single<FeedbackInteractor> { FeedbackInteractorImpl(get(),get(),androidContext()) }
    single<JsoupUpdateInteractor> { JsoupUpdateInteractorImpl(get(), get(), get(), get()) }
}

val presentationModule = module {
    viewModel { HomeViewModel(get(), get()) }
    viewModel { (id: Long) -> DetailsViewModel(id, get(), get(), get(), get()) }
    viewModel { ExtendedSearchViewModel(get()) }
    viewModel { FavoritesViewModel(get()) }
    viewModel { HistoryViewModel(get(), get()) }
    viewModel { PlayerViewModel(get(), get(), get()) }
    viewModel { SplashViewModel(get()) }
    viewModel { TvHomeViewModel(get(), get(), get()) }
    viewModel { TvDetailsViewModel(get()) }
    viewModel { TvSearchViewModel(get()) }
}
