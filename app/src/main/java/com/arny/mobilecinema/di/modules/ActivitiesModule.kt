package com.arny.mobilecinema.di.modules

import com.arny.mobilecinema.di.scopes.ActivityScope
import com.arny.mobilecinema.presentation.MainActivity
import com.arny.mobilecinema.presentation.di.DetailsFragmentModule
import com.arny.mobilecinema.presentation.di.HistoryFragmentModule
import com.arny.mobilecinema.presentation.di.HomeFragmentModule
import com.arny.mobilecinema.presentation.di.PlayerFragmentModule
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
        ]
    )
    abstract fun bindMainActivity(): MainActivity
}
