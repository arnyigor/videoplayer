package com.arny.mobilecinema.data.db.daos

import androidx.room.Dao
import androidx.room.Query
import com.arny.mobilecinema.data.db.models.HistoryEntity
import com.arny.mobilecinema.domain.models.HistoryItem
import com.arny.mobilecinema.domain.models.ViewMovie

@Dao
interface HistoryDao : BaseDao<HistoryEntity> {
    @Query("SELECT movie_dbid FROM history ORDER BY dbId DESC")
    suspend fun getHistoryIds(): List<HistoryItem>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE dbId IN(:ids) ORDER BY updated DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedList(ids: List<Long>, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE dbId IN(:ids) AND title LIKE '%' || :search || '%' ORDER BY dbId ASC LIMIT :limit OFFSET :offset")
    suspend fun getPagedList(
        ids: List<Long>,
        search: String,
        limit: Int,
        offset: Int
    ): List<ViewMovie>

    @Query("SELECT * FROM history WHERE movie_dbid =:movieDbId")
    fun getHistory(movieDbId: Long?): HistoryEntity?

    @Query("UPDATE history SET position=:position, season=:season, episode=:episode WHERE movie_dbid =:movieDbId")
    fun updateHistory(movieDbId: Long?, position: Long = 0, season: Int = 0, episode: Int = 0)

    @Query("DELETE FROM history WHERE movie_dbid =:movieDbId")
    fun deleteHistory(movieDbId: Long?): Int

    @Query("DELETE FROM history")
    fun deleteAllHistory(): Int
}