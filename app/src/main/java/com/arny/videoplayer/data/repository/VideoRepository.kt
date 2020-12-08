package com.arny.videoplayer.data.repository

import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    fun searchVideo(search: String): Flow<String>
    fun all(): Flow<String>
}