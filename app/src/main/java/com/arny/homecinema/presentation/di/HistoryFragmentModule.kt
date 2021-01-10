package com.arny.homecinema.presentation.di

import com.arny.homecinema.di.scopes.FragmentScope
import com.arny.homecinema.presentation.history.HistoryFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
interface HistoryFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector
    fun contributeFragmentInjector(): HistoryFragment
}
