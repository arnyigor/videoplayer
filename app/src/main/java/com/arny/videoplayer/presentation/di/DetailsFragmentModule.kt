package com.arny.videoplayer.presentation.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.arny.videoplayer.data.repository.VideoRepository
import com.arny.videoplayer.di.scopes.FragmentScope
import com.arny.videoplayer.di.scopes.ViewModelKey
import com.arny.videoplayer.presentation.details.DetailsFragment
import com.arny.videoplayer.presentation.details.DetailsViewModel
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.multibindings.IntoMap

@Module(
    includes = [
        DetailsFragmentModule.ProvideViewModel::class
    ]
)
interface DetailsFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector(
        modules = [
            InjectViewModel::class
        ]
    )
    fun contributeFragmentInjector(): DetailsFragment

    @Module
    class ProvideViewModel {

        @Provides
        @IntoMap
        @ViewModelKey(DetailsViewModel::class)
        fun provideViewModel(videoRepository: VideoRepository): ViewModel =
            DetailsViewModel(videoRepository)
    }

    @Module
    class InjectViewModel {

        @Provides
        fun provideViewModel(
            factory: ViewModelProvider.Factory,
            target: DetailsFragment
        ) = ViewModelProvider(target, factory).get(DetailsViewModel::class.java)
    }
}