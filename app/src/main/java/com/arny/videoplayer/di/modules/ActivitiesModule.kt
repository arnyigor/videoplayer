package com.arny.videoplayer.di.modules

import com.arny.videoplayer.di.scopes.ActivityScope
import com.arny.videoplayer.presentation.MainActivity
import com.arny.videoplayer.presentation.di.DetailsFragmentModule
import com.arny.videoplayer.presentation.di.HomeFragmentModule
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class ActivitiesModule {
    @ActivityScope
    @ContributesAndroidInjector(
        modules = [
            HomeFragmentModule::class,
            DetailsFragmentModule::class
        ]
    )
    abstract fun bindMainActivity(): MainActivity
}
