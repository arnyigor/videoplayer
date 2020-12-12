package com.arny.homecinema.di

import com.arny.homecinema.VideoApp
import com.arny.homecinema.data.di.DataModule
import com.arny.homecinema.data.network.NetworkModule
import com.arny.homecinema.di.modules.AppModule
import com.arny.homecinema.di.modules.UiModule
import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjector
import dagger.android.support.AndroidSupportInjectionModule
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AndroidSupportInjectionModule::class,
        AppModule::class,
        UiModule::class,
        DataModule::class,
        NetworkModule::class
    ]
)
interface AppComponent : AndroidInjector<VideoApp> {
    override fun inject(application: VideoApp)

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(application: VideoApp): Builder

        fun build(): AppComponent
    }
}