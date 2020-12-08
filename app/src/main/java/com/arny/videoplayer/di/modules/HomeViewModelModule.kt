package com.arny.videoplayer.di.modules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.arny.videoplayer.data.repository.VideoRepository
import com.arny.videoplayer.di.scopes.ViewModelKey
import com.arny.videoplayer.presentation.home.HomeFragment
import com.arny.videoplayer.presentation.home.HomeViewModel
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