package com.arny.homecinema.di.modules

import android.content.Context
import com.arny.homecinema.VideoApp
import dagger.Binds
import dagger.Module

@Module
internal abstract class AppModule {
    @Binds
    abstract fun provideContext(application: VideoApp): Context
}