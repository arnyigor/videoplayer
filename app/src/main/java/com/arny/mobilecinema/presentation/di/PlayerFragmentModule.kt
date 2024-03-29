package com.arny.mobilecinema.presentation.di

import com.arny.mobilecinema.di.scopes.FragmentScope
import com.arny.mobilecinema.presentation.playerview.PlayerViewFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
interface PlayerFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector
    fun contributeFragmentInjector(): PlayerViewFragment
}
