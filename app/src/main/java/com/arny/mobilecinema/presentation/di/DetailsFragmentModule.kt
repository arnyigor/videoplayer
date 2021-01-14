package com.arny.mobilecinema.presentation.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.arny.mobilecinema.data.repository.VideoRepository
import com.arny.mobilecinema.di.scopes.FragmentScope
import com.arny.mobilecinema.di.scopes.ViewModelKey
import com.arny.mobilecinema.presentation.details.DetailsFragment
import com.arny.mobilecinema.presentation.details.DetailsViewModel
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