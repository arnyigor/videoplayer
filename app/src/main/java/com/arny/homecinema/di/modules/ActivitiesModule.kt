package com.arny.homecinema.di.modules

import com.arny.homecinema.di.scopes.ActivityScope
import com.arny.homecinema.presentation.MainActivity
import com.arny.homecinema.presentation.di.DetailsFragmentModule
import com.arny.homecinema.presentation.di.HomeFragmentModule
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class ActivitiesModule {
    @ActivityScope
    @ContributesAndroidInjector(
        modules = [
            HomeFragmentModule::class,
            DetailsFragmentModule::class,
        ]
    )
    abstract fun bindMainActivity(): MainActivity
}
