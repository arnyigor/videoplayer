package com.arny.mobilecinema.presentation.di

import com.arny.mobilecinema.di.scopes.FragmentScope
import com.arny.mobilecinema.presentation.extendedsearch.ExtendedSearchFragment
import com.arny.mobilecinema.presentation.history.HistoryFragment
import com.arny.mobilecinema.presentation.home.HomeFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
interface ExtendedSearchFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector
    fun contributeFragmentInjector(): ExtendedSearchFragment
}
