package com.arny.mobilecinema.data.db.daos

import androidx.room.*
import com.arny.mobilecinema.data.db.models.FavoriteEntity

@Dao
interface FavoritesDao : BaseDao<FavoriteEntity> {

    // Count how many rows exist for a given movie ID
    @Query("SELECT COUNT(*) FROM favorites WHERE movie_dbid = :movieDbId")
    fun getCountForMovie(movieDbId: Long): Int

    // Existing helpers (you already have these)
    @Query("DELETE FROM favorites WHERE movie_dbid=:movieDbId")
    fun deleteFavorite(movieDbId: Long?): Int

    @Query("DELETE FROM favorites")
    fun deleteAllFavorites(): Int

    @Query("SELECT COUNT(*) FROM favorites WHERE movie_dbid !=0 AND movie_dbid IS NOT NULL")
    fun getFavoritesCount(): Int

    @Query("UPDATE favorites SET latest_time = :time WHERE movie_dbid IN (SELECT movie_dbid FROM favorites)")
    suspend fun updateLatestTime(time: Long)
}