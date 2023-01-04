package com.arny.mobilecinema.di

import com.arny.mobilecinema.VideoApp
import com.arny.mobilecinema.data.di.DataModule
import com.arny.mobilecinema.data.network.NetworkModule
import com.arny.mobilecinema.di.modules.AppModule
import com.arny.mobilecinema.di.modules.UiModule
import com.arny.mobilecinema.domain.di.DomainModule
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
        DomainModule::class,
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