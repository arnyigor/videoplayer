package com.arny.homecinema.presentation.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.arny.homecinema.data.repository.VideoRepository
import com.arny.homecinema.di.scopes.FragmentScope
import com.arny.homecinema.di.scopes.ViewModelKey
import com.arny.homecinema.presentation.home.HomeFragment
import com.arny.homecinema.presentation.home.HomeViewModel
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.multibindings.IntoMap

@Module(
    includes = [
        HomeFragmentModule.ProvideViewModel::class
    ]
)
interface HomeFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector(
        modules = [
            InjectViewModel::class
        ]
    )
    fun contributeFragmentInjector(): HomeFragment

    @Module
    class ProvideViewModel {

        @Provides
        @IntoMap
        @ViewModelKey(HomeViewModel::class)
        fun provideHomeViewModel(videoRepository: VideoRepository): ViewModel =
            HomeViewModel(videoRepository)
    }

    @Module
    class InjectViewModel {

        @Provides
        fun provideHomeViewModel(
            factory: ViewModelProvider.Factory,
            target: HomeFragment
        ) = ViewModelProvider(target, factory).get(HomeViewModel::class.java)
    }
}