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
import com.arny.mobilecinema.data.repository.playback.PlaybackProgressStore
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
import com.arny.mobilecinema.domain.interactors.update.CatalogUpdatePhase
import com.arny.mobilecinema.domain.interactors.update.CatalogUpdateStatus
import com.arny.mobilecinema.domain.interactors.update.CatalogUpdateTracker
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractorImpl
import com.arny.mobilecinema.domain.playback.NextEpisodeResolver
import com.arny.mobilecinema.domain.playback.ProgressCompletionPolicy
import com.arny.mobilecinema.domain.repository.JsoupUpdateRepository
import com.arny.mobilecinema.domain.repository.MoviesRepository
import com.arny.mobilecinema.domain.repository.UpdateRepository
import com.arny.mobilecinema.presentation.comments.CommentsViewModel
import com.arny.mobilecinema.presentation.details.DetailsViewModel
import com.arny.mobilecinema.presentation.extendedsearch.ExtendedSearchViewModel
import com.arny.mobilecinema.presentation.settings.CatalogSyncViewModel
import com.arny.mobilecinema.presentation.sitesearch.SiteSearchViewModel
import com.arny.mobilecinema.presentation.favorite.FavoritesViewModel
import com.arny.mobilecinema.presentation.history.HistoryViewModel
import com.arny.mobilecinema.presentation.home.HomeViewModel
import com.arny.mobilecinema.presentation.player.PlayerSource
import com.arny.mobilecinema.presentation.mobile.home.MobileHomeViewModel
import com.arny.mobilecinema.presentation.playerview.PlayerViewModel
import com.arny.mobilecinema.presentation.splash.SplashViewModel
import com.arny.mobilecinema.presentation.catalog.CatalogSection
import com.arny.mobilecinema.presentation.catalog.CatalogViewModel
import com.arny.mobilecinema.presentation.tv.home.TvHomeViewModel
import com.arny.mobilecinema.presentation.tv.search.TvSearchViewModel
import com.arny.mobilecinema.presentation.tv.viewmodel.TvDetailsViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import org.koin.core.qualifier.named

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
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
            )
            .build()
    }
    single { get<AppDatabase>().movieDao() }
    single { get<AppDatabase>().historyDao() }
    single { get<AppDatabase>().favoritesDao() }
    single { get<AppDatabase>().playbackProgressDao() }
    single<FeedbackDatabase> { FeedbackDatabaseImpl(get(),get()) }
    single<UpdateRepository> { UpdateRepositoryImpl(get(), get(), get(), androidContext(), get()) }
    single<MoviesRepository> { MoviesRepositoryImpl(get(), get(), get(), get(), get(), get(), get()) }
    single<JsoupUpdateRepository> { JsoupUpdateRepositoryImpl(get(), get()) }
    single<AppResourcesProvider> { AppResourcesProviderImpl(androidContext()) }
    single {
        PlaybackProgressStore(
            playbackProgressDao = get(),
            historyDao = get(),
            policy = get(),
            dispatcher = get<CoroutineDispatcher>(),
        )
    }
    single { PlayerSource(androidContext(), get(), get<CoroutineDispatcher>()) }
}

val domainModule = module {
    single {
        val prefs = get<Prefs>()
        CatalogUpdateTracker(CatalogUpdateStatus(
            phase = CatalogUpdatePhase.entries.firstOrNull { it.name == prefs.get<String>("catalog_update_phase") }
                ?: CatalogUpdatePhase.IDLE,
            progress = prefs.get<Int>("catalog_update_progress")?.takeIf { it >= 0 },
            processedMovies = prefs.get<Int>("catalog_update_count")?.takeIf { it >= 0 },
        )) { status ->
            prefs.settings.edit().putString("catalog_update_phase", status.phase.name)
                .putInt("catalog_update_progress", status.progress ?: -1)
                .putInt("catalog_update_count", status.processedMovies ?: -1).apply()
        }
    }
    single { ProgressCompletionPolicy() }
    single { NextEpisodeResolver(get()) }
    single<MoviesInteractor> { MoviesInteractorImpl(get(), get(), get(), get<CoroutineDispatcher>()) }
    single<HistoryInteractor> { HistoryInteractorImpl(get(), get<CoroutineDispatcher>(), get()) }
    single<DataUpdateInteractor> { DataUpdateInteractorImpl(get()) }
    single<FeedbackInteractor> { FeedbackInteractorImpl(get(),get(),androidContext()) }
    single<JsoupUpdateInteractor> { JsoupUpdateInteractorImpl(get(), get(), get(), get()) }
}

val presentationModule = module {
    viewModel { HomeViewModel(get(), get()) }
    viewModel { (id: Long) -> DetailsViewModel(id, get(), get(), get(), get(), get()) }
    viewModel { (pageUrl: String, title: String) -> CommentsViewModel(pageUrl, title, get()) }
    viewModel { ExtendedSearchViewModel(get()) }
    viewModel { SiteSearchViewModel(get(), get(), get()) }
    viewModel { CatalogSyncViewModel(get(), get()) }
    viewModel { FavoritesViewModel(get()) }
    viewModel { HistoryViewModel(get(), get(), get()) }
    viewModel { PlayerViewModel(get(), get(), get(), get()) }
    viewModel { MobileHomeViewModel(get(), get(), get()) }
    viewModel { SplashViewModel(get()) }
    viewModel { TvHomeViewModel(get(), get(), get(), get(), get()) }
    viewModel { (sectionKey: String) -> CatalogViewModel(get(), sectionKey, get()) }
    // The qualifier is also part of the ViewModelStore key within HomeFragment.
    viewModel(named(CatalogSection.FILMS.name)) { CatalogViewModel(get(), CatalogSection.FILMS.name, get()) }
    viewModel(named(CatalogSection.SERIES.name)) { CatalogViewModel(get(), CatalogSection.SERIES.name, get()) }
    viewModel { TvDetailsViewModel(get(), get(), get()) }
    viewModel { TvSearchViewModel(get()) }
    viewModel { com.arny.mobilecinema.presentation.tv.library.TvLibraryViewModel(get(), get(), get()) }
    viewModel { com.arny.mobilecinema.presentation.tv.settings.TvSettingsViewModel(get()) }
}
