package com.arny.mobilecinema.presentation.di

import com.arny.mobilecinema.di.scopes.FragmentScope
import com.arny.mobilecinema.presentation.details.DetailsFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
interface DetailsFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector(
        modules = [
            DetailsFragmentDependencies::class
        ]
    )
    fun contributeFragmentInjector(): DetailsFragment
}
