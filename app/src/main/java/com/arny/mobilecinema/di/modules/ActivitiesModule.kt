package com.arny.mobilecinema.di.modules

import com.arny.mobilecinema.di.scopes.ActivityScope
import com.arny.mobilecinema.presentation.MainActivity
import com.arny.mobilecinema.presentation.di.DetailsFragmentModule
import com.arny.mobilecinema.presentation.di.ExtendedSearchFragmentModule
import com.arny.mobilecinema.presentation.di.HistoryFragmentModule
import com.arny.mobilecinema.presentation.di.HomeFragmentModule
import com.arny.mobilecinema.presentation.di.PlayerFragmentModule
import com.arny.mobilecinema.presentation.splash.StartActivity
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class ActivitiesModule {
    @ActivityScope
    @ContributesAndroidInjector(
        modules = [
            PlayerFragmentModule::class,
            HomeFragmentModule::class,
            DetailsFragmentModule::class,
            HistoryFragmentModule::class,
            ExtendedSearchFragmentModule::class,
        ]
    )
    abstract fun bindMainActivity(): MainActivity

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindSplashActivity(): StartActivity
}
