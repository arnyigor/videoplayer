package com.arny.videoplayer.di.modules

import android.content.Context
import com.arny.videoplayer.VideoApp
import dagger.Binds
import dagger.Module

@Module
internal abstract class AppModule {
    @Binds
    abstract fun provideContext(application: VideoApp): Context
}