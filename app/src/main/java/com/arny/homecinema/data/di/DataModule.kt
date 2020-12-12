package com.arny.homecinema.data.di

import com.arny.homecinema.data.repository.VideoRepository
import com.arny.homecinema.data.repository.VideoRepositoryImpl
import dagger.Binds
import dagger.Module
import javax.inject.Singleton

@Module
interface DataModule {
    @Binds
    @Singleton
    fun bindsVideoRepository(repository: VideoRepositoryImpl): VideoRepository
}