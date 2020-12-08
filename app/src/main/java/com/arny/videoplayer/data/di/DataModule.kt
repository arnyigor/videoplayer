package com.arny.videoplayer.data.di

import com.arny.videoplayer.data.repository.VideoRepository
import com.arny.videoplayer.data.repository.VideoRepositoryImpl
import dagger.Binds
import dagger.Module
import javax.inject.Singleton

@Module
interface DataModule {
    @Binds
    @Singleton
    fun bindsVideoRepository(repository: VideoRepositoryImpl): VideoRepository
}