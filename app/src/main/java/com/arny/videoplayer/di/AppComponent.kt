package com.arny.videoplayer.di

import com.arny.videoplayer.VideoApp
import com.arny.videoplayer.data.di.DataModule
import com.arny.videoplayer.data.network.NetworkModule
import com.arny.videoplayer.di.modules.UiModule
import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjector
import dagger.android.support.AndroidSupportInjectionModule
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AndroidSupportInjectionModule::class,
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