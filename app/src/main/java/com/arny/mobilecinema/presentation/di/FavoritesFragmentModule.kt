package com.arny.mobilecinema.presentation.di

import com.arny.mobilecinema.di.scopes.FragmentScope
import com.arny.mobilecinema.presentation.favorite.FavoritesFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
interface FavoritesFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector
    fun contributeFragmentInjector(): FavoritesFragment
}
