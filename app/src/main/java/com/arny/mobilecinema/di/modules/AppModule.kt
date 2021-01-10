package com.arny.mobilecinema.di.modules

import android.content.Context
import com.arny.mobilecinema.VideoApp
import dagger.Binds
import dagger.Module

@Module
internal abstract class AppModule {
    @Binds
    abstract fun provideContext(application: VideoApp): Context
}