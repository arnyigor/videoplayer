package com.arny.mobilecinema.data.db.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.arny.mobilecinema.data.db.models.MovieEntity
import com.arny.mobilecinema.data.db.models.MovieUpdate
import com.arny.mobilecinema.data.db.models.PersonalizationMovieSignal
import com.arny.mobilecinema.domain.models.SimpleIntRange

@Dao
interface MovieDao : BaseDao<MovieEntity> {

    @Query("SELECT COUNT(*) FROM movies")
    fun getCount(): Int

    /** Удаляем все строки, у которых совпадают title+pageUrl,
    но PK отличается от переданного. */
    @Query(
        """
        DELETE FROM movies 
        WHERE title = :title AND pageUrl = :url AND dbId != :excludeId
        """
    )
    suspend fun deleteConflicts(title: String, url: String, excludeId: Long)

    /** Объединённый метод – атомарно удаляем конфликт и вставляем/обновляем. */
    @Transaction
    suspend fun safeUpsert(movie: MovieEntity) {
        deleteConflicts(movie.title, movie.pageUrl, movie.dbId)
        // Если запись уже есть (по PK), делаем update, иначе insert.
        val rowsUpdated = update(movie)
        if (rowsUpdated == 0) {
            insert(movie)
        }
    }

    @Query("DELETE FROM movies")
    fun deleteAll(): Int

    @Query("DELETE FROM movies WHERE dbId in (:idList)")
    fun deleteAll(idList: List<Long>): Int

    @Query(
        """
        SELECT dbId, pageUrl, title, updated, genre, detailsFetchedAt
        FROM movies
        """
    )
    fun getUpdateMovies(): List<MovieUpdate>

    @Query("SELECT * FROM movies WHERE dbId = :id")
    fun getMovie(id: Long): MovieEntity?

    @Query("SELECT * FROM movies WHERE pageUrl = :pageUrl")
    fun getMovie(pageUrl: String): MovieEntity?

    @Query("SELECT * FROM movies WHERE img = :imgUrl")
    fun getMovieByImg(imgUrl: String): MovieEntity?

    @Query(
        """
        SELECT
            m.*,
            f.latest_time AS favoriteLatestTime,
            COALESCE(SUM(p.played_ms), 0) AS playbackMs,
            COALESCE(SUM(CASE WHEN p.played_ms >= :meaningfulEpisodeMs THEN 1 ELSE 0 END), 0) AS meaningfulEpisodes
        FROM movies m
        LEFT JOIN favorites f ON m.dbId = f.movie_dbid
        LEFT JOIN playback_progress p ON m.dbId = p.movie_dbid
        WHERE p.movie_dbid IS NOT NULL OR f.movie_dbid IS NOT NULL
        GROUP BY m.dbId
        """
    )
    suspend fun getPersonalizationSignals(meaningfulEpisodeMs: Long): List<PersonalizationMovieSignal>

    @Query(
        """
        SELECT m.*
        FROM movies m
        WHERE m.type IN (:movieTypes)
            AND m.dbId NOT IN (SELECT movie_dbid FROM history WHERE movie_dbid != 0)
            AND m.dbId NOT IN (SELECT movie_dbid FROM favorites WHERE movie_dbid != 0)
            AND m.dbId NOT IN (SELECT movie_dbid FROM playback_progress WHERE movie_dbid != 0)
        ORDER BY
            m.year DESC,
            m.likes DESC,
            m.ratingImdb DESC,
            m.ratingKp DESC,
            m.dbId DESC
        LIMIT :limit
        """
    )
    suspend fun getPersonalizationCandidates(
        movieTypes: List<Int>,
        limit: Int
    ): List<MovieEntity>

    @Query("SELECT dbId FROM movies ORDER BY dbId DESC LIMIT 1")
    fun getLastId(): Long

    @Query("SELECT DISTINCT genre FROM movies")
    fun allGenres(): List<String>

    @Query("SELECT MIN(year) as `from`, MAX(year) as `to` FROM movies WHERE year > 1900")
    fun getYearsMinMax(): SimpleIntRange

    @Query("UPDATE movies SET customData=:customData WHERE pageUrl = :pageUrl")
    fun updateCustomData(customData: String?, pageUrl: String): Int

    /**
     * Поиск строки, которая могла бы нарушить уникальный индекс.
     * Возвращает null, если такой строки нет.
     */
    @Query("""
        SELECT *
        FROM movies
        WHERE title = :title AND pageUrl = :pageUrl
        LIMIT 1
    """)
    suspend fun findByTitleAndPageUrl(title: String, pageUrl: String): MovieEntity?
}
