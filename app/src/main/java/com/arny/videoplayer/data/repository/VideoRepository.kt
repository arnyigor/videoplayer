package com.arny.videoplayer.data.repository

import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    fun searchVideo(): Flow<String>
}