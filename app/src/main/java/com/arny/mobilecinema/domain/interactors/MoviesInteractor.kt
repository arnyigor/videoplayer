package com.arny.mobilecinema.domain.interactors

import androidx.paging.PagingData
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.SaveData
import com.arny.mobilecinema.domain.models.ViewMovie
import kotlinx.coroutines.flow.Flow

interface MoviesInteractor {
    fun getMovies(search: String = ""): Flow<PagingData<ViewMovie>>
    fun getMovie(id: Long): Flow<DataResult<Movie>>
    suspend fun saveCinemaPosition(id: Long?, position: Long)
    suspend fun saveSerialPosition(
        id: Long?,
        season: Int,
        episode: Int,
        episodePosition: Long
    )

    fun getSaveData(dbId: Long?): Flow<DataResult<SaveData>>
    fun getHistoryMovies(search: String): Flow<PagingData<ViewMovie>>
    fun isHistoryEmpty(): Flow<DataResult<Boolean>>
    fun clearCache(dbId: Long?): Flow<DataResult<Boolean>>
}