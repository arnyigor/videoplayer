package com.arny.homecinema.data.repository

import com.arny.homecinema.data.models.DataResult
import com.arny.homecinema.di.models.MainPageContent
import com.arny.homecinema.di.models.Video
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    fun searchVideo(search: String): Flow<List<Video>>
    fun getAllVideos(): Flow<DataResult<MainPageContent>>
    fun getAllVideos(type: String?): Flow<DataResult<MainPageContent>>
    fun loadVideo(video: Video): Flow<DataResult<Video>>
}