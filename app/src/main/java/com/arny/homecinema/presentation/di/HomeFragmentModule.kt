package com.arny.homecinema.presentation.di

import com.arny.homecinema.di.scopes.FragmentScope
import com.arny.homecinema.presentation.home.HomeFragment
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
