package com.arny.homecinema.di.modules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.arny.homecinema.data.repository.VideoRepository
import com.arny.homecinema.di.scopes.ViewModelKey
import com.arny.homecinema.presentation.home.HomeFragment
import com.arny.homecinema.presentation.home.HomeViewModel
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.multibindings.IntoMap

@Module(
    includes = [HomeViewModelModule.ProvideViewModel::class]
)
abstract class HomeViewModelModule {

    @ContributesAndroidInjector(
        modules = [
            InjectViewModel::class
        ]
    )
    abstract fun bind(): HomeFragment

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