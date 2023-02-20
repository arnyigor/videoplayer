package com.arny.mobilecinema.data.db.daos

import androidx.room.Dao
import androidx.room.Query
import com.arny.mobilecinema.data.db.models.MovieEntity
import com.arny.mobilecinema.data.db.models.MovieUpdate
import com.arny.mobilecinema.domain.models.ViewMovie

@Dao
interface MovieDao : BaseDao<MovieEntity> {
    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies ORDER BY updated DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedList(limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies " +
            "ORDER BY title ASC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListRatingTitle(limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies " +
            "ORDER BY year DESC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListRatingYearD(limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies " +
            "ORDER BY year ASC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListRatingYearA(limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE title LIKE '%' || :search || '%' ORDER BY dbId ASC LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchTitle(search: String, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE directors LIKE '%' || :search || '%' ORDER BY dbId ASC LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchDirectors(search: String, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE actors LIKE '%' || :search || '%' ORDER BY dbId ASC LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchActors(search: String, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE genre LIKE '%' || :search || '%' ORDER BY dbId ASC LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchGenres(search: String, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE CASE " +
            "WHEN :searchType = 'title' THEN title LIKE '%' || :search || '%' " +
            "WHEN :searchType = 'directors' THEN directors LIKE '%' || :search || '%' " +
            "WHEN :searchType = 'actors' THEN actors LIKE '%' || :search || '%' " +
            "WHEN :searchType = 'genres' THEN genre LIKE '%' || :search || '%' " +
            "ELSE title LIKE '%' || :search || '%' " +
            "END " +
            "ORDER BY  " +
            "CASE WHEN :order = 'ratingKpD' THEN ratingKp END DESC, " +
            "CASE WHEN :order = 'ratingImdbD' THEN ratingImdb END DESC, " +
            "CASE WHEN :order = 'updatedD' THEN updated END DESC, " +
            "CASE WHEN :order = 'updatedA' THEN updated END ASC, " +
            "CASE WHEN :order = 'yearD' THEN year END DESC, " +
            "CASE WHEN :order = 'yearA' THEN year END ASC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearch(
        search: String,
        searchType: String,
        order: String,
        limit: Int,
        offset: Int
    ): List<ViewMovie>

    @Query("SELECT COUNT(dbId) FROM movies")
    fun getCount(): Int

    @Query("SELECT dbId, pageUrl, updated FROM movies")
    fun getUpdateMovies(): List<MovieUpdate>

    @Query("SELECT * FROM movies WHERE dbId = :id")
    fun getMovie(id: Long): MovieEntity?
}