package com.arny.homecinema.presentation.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.arny.homecinema.data.repository.VideoRepository
import com.arny.homecinema.di.scopes.FragmentScope
import com.arny.homecinema.di.scopes.ViewModelKey
import com.arny.homecinema.presentation.history.HistoryFragment
import com.arny.homecinema.presentation.history.HistoryViewModel
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.multibindings.IntoMap

@Module(
    includes = [
        HistoryFragmentModule.ProvideViewModel::class
    ]
)
interface HistoryFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector(
        modules = [
            InjectViewModel::class
        ]
    )
    fun contributeFragmentInjector(): HistoryFragment

    @Module
    class ProvideViewModel {

        @Provides
        @IntoMap
        @ViewModelKey(HistoryViewModel::class)
        fun provideHistoryViewModel(videoRepository: VideoRepository): ViewModel =
            HistoryViewModel(videoRepository)
    }

    @Module
    class InjectViewModel {

        @Provides
        fun provideHistoryViewModel(
            factory: ViewModelProvider.Factory,
            target: HistoryFragment
        ) = ViewModelProvider(target, factory).get(HistoryViewModel::class.java)
    }
}