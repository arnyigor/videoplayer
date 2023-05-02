package com.arny.mobilecinema.data.db.daos

import androidx.room.Dao
import androidx.room.Query
import com.arny.mobilecinema.data.db.models.MovieEntity
import com.arny.mobilecinema.data.db.models.MovieUpdate
import com.arny.mobilecinema.domain.models.ViewMovie

@Dao
interface MovieDao : BaseDao<MovieEntity> {
    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies " +
            "ORDER BY updated DESC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListOrderUpdated(limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies " +
            "ORDER BY ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListOrderRatings(limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies " +
            "ORDER BY title ASC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListOrderTitle(limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies " +
            "ORDER BY year DESC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListOrderYearD(limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies " +
            "ORDER BY year ASC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListOrderYearA(limit: Int, offset: Int): List<ViewMovie>

    /*Title*/
    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE title LIKE '%' || :search || '%' " +
            "ORDER BY updated DESC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchTitleOrderUpdated(search: String, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE title LIKE '%' || :search || '%' " +
            "ORDER BY ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchTitleOrderRating(search: String, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE title LIKE '%' || :search || '%' " +
            "ORDER BY title ASC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchTitleOrderTitle(search: String, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE title LIKE '%' || :search || '%' " +
            "ORDER BY year DESC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchTitleOrderYearD(search: String, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE title LIKE '%' || :search || '%' " +
            "ORDER BY year ASC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchTitleOrderYearA(search: String, limit: Int, offset: Int): List<ViewMovie>

    /*Directors*/
    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE directors LIKE '%' || :search || '%' " +
            "ORDER BY updated DESC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchDirectorsOrderUpdated(search: String, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE directors LIKE '%' || :search || '%' " +
            "ORDER BY ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchDirectorsOrderRating(search: String, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE directors LIKE '%' || :search || '%' " +
            "ORDER BY title ASC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchDirectorsOrderTitle(search: String, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE directors LIKE '%' || :search || '%' " +
            "ORDER BY year DESC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchDirectorsOrderYearD(search: String, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE directors LIKE '%' || :search || '%' " +
            "ORDER BY year ASC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchDirectorsOrderYearA(search: String, limit: Int, offset: Int): List<ViewMovie>

    /*Actors*/
    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE actors LIKE '%' || :search || '%' " +
            "ORDER BY updated DESC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchActorsOrderUpdated(search: String, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE actors LIKE '%' || :search || '%' " +
            "ORDER BY ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchActorsOrderRating(search: String, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE actors LIKE '%' || :search || '%' " +
            "ORDER BY title ASC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchActorsOrderTitle(search: String, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE actors LIKE '%' || :search || '%' " +
            "ORDER BY year DESC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchActorsOrderYearD(search: String, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE actors LIKE '%' || :search || '%' " +
            "ORDER BY year ASC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchActorsOrderYearA(search: String, limit: Int, offset: Int): List<ViewMovie>

    /*Genres*/
    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE genre LIKE '%' || :search || '%' " +
            "ORDER BY updated DESC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchGenresOrderUpdated(search: String, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE genre LIKE '%' || :search || '%' " +
            "ORDER BY ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchGenresOrderRating(search: String, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE genre LIKE '%' || :search || '%' " +
            "ORDER BY title ASC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchGenresOrderTitle(search: String, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE genre LIKE '%' || :search || '%' " +
            "ORDER BY year DESC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchGenresOrderYearD(search: String, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT dbId, title, type, img, year, likes, dislikes FROM movies WHERE genre LIKE '%' || :search || '%' " +
            "ORDER BY year ASC, ratingImdb DESC, ratingKp DESC, likes DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getPagedListBySearchGenresOrderYearA(search: String, limit: Int, offset: Int): List<ViewMovie>

    @Query("SELECT COUNT(dbId) FROM movies")
    fun getCount(): Int

    @Query("SELECT dbId, pageUrl, title, updated FROM movies")
    fun getUpdateMovies(): List<MovieUpdate>

    @Query("SELECT * FROM movies WHERE dbId = :id")
    fun getMovie(id: Long): MovieEntity?
}