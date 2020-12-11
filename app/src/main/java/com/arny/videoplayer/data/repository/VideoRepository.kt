package com.arny.videoplayer.data.repository

import com.arny.videoplayer.data.models.DataResult
import com.arny.videoplayer.di.models.Video
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    fun searchVideo(search: String): Flow<String>
    fun getAllVideos(): Flow<List<Video>>
    fun loadVideo(video: Video): Flow<DataResult<Video>>
}