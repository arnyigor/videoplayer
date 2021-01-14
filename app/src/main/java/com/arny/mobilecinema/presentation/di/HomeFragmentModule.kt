package com.arny.mobilecinema.presentation.di

import com.arny.mobilecinema.di.scopes.FragmentScope
import com.arny.mobilecinema.presentation.home.HomeFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
interface HomeFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector(
        modules = [
            HomeFragmentDependencies::class
        ]
    )
    fun contributeFragmentInjector(): HomeFragment
}
