package com.arny.mobilecinema.domain.interactors.history

import androidx.paging.PagingData
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SaveData
import com.arny.mobilecinema.domain.models.ViewMovie
import kotlinx.coroutines.flow.Flow

interface HistoryInteractor {
    val serialPositionChange: Flow<Boolean>
    suspend fun saveCinemaPosition(movieDbId: Long, time: Long): Boolean
    suspend fun saveSerialPosition(
        movieDbId: Long,
        season: Int,
        episode: Int,
        time: Long
    ): Boolean

    fun getSaveData(movieDbId: Long?): Flow<DataResult<SaveData>>
    fun getHistoryMovies(
        search: String = "",
        order: String,
        searchType: String
    ): Flow<PagingData<ViewMovie>>
    fun clearViewHistory(movieDbId: Long?, type: MovieType?, total: Boolean, url: String): Flow<DataResult<Boolean>>
    fun addToViewHistory(movieDbId: Long): Flow<DataResult<Boolean>>
    fun clearAllViewHistory(): Flow<DataResult<Boolean>>
    fun isHistoryEmpty(): Flow<DataResult<Boolean>>
    fun setSerialPositionChange(change: Boolean)
}