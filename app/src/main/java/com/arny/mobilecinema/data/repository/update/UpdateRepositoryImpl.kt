package com.arny.mobilecinema.data.repository.update

import android.content.Context
import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.data.api.ApiService
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.db.models.MovieEntity
import com.arny.mobilecinema.data.db.models.MovieUpdate
import com.arny.mobilecinema.data.models.setData
import com.arny.mobilecinema.data.network.jsoup.JsoupService
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.repository.prefs.PrefsConstants
import com.arny.mobilecinema.data.utils.create
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.repository.UpdateRepository
import java.io.File
import javax.inject.Inject

class UpdateRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val jsoup: JsoupService,
    private val prefs: Prefs,
    private val context: Context,
    private val moviesDao: MovieDao
) : UpdateRepository {
    override var checkUpdate: Boolean = false
    override var newUpdate: String = ""
    override var updateDownloadId: Long = -1L
    override var lastUpdate: String
        get() = prefs.get<String>(PrefsConstants.LAST_DATA_UPDATE).orEmpty()
        set(value) {
            prefs.put(PrefsConstants.LAST_DATA_UPDATE, value)
        }
    override var baseUrl: String
        get() = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()
        set(value) {
            prefs.put(PrefsConstants.BASE_URL, value)
        }

    override fun setLastUpdate() {
        lastUpdate = newUpdate
        newUpdate = ""
    }

    override suspend fun downloadFile(url: String, name: String): File {
        val file = File(context.filesDir, name)
        file.delete()
        file.create()
        apiService.downloadFile(file, url)
        return file
    }

    override fun updateMovies(movies: List<Movie>, forceAll: Boolean, onUpdate: (ind: Int) -> Unit) {
        var entity = MovieEntity()
        val size = movies.size
        if (moviesDao.getCount() == 0) {
            for ((ind, movie) in movies.withIndex()) {
                entity = entity.setData(movie)
                moviesDao.insert(entity)
                if (ind % 1000 == 0) {
                    onUpdate(getPercent(ind, size))
                }
            }
        } else {
            val dbList = moviesDao.getUpdateMovies()
            movies.forEachIndexed { index, movie ->
                val dbMovies = dbList.filter { it.pageUrl == movie.pageUrl }
                val notCorrectDbMovies = dbMovies.filter { isEqualsUrlAndNotTitle(it, movie) }
                notCorrectDbMovies.forEach { ncm ->
                    entity.clear()
                    movies.find { serverMovie -> ncm.title.equals(serverMovie.title, true) }
                        ?.let { correctMovieByName ->
                            entity = entity.setData(correctMovieByName).copy(dbId = ncm.dbId)
                            try {
                                moviesDao.update(entity)
                            } catch (e: Exception) {
                                error("Update error for $entity has error:${e.stackTraceToString()} with notCorrectedMovies:$notCorrectDbMovies")
                            }
                        }
                }
                val dbMovie = dbMovies.find { isEqualsUrlAndTitle(it, movie) }
                entity.clear()
                try {
                    val dbId = dbMovie?.dbId
                    if (dbId != null) {
                        entity = entity.setData(movie).copy(dbId = dbId)
                        moviesDao.update(entity)
                    } else {
                        val newId = moviesDao.getLastId() + 1
                        entity = entity.setData(movie).copy(dbId = newId)
                        moviesDao.insert(entity)
                    }
                } catch (e: Exception) {
                    error("Update error for $entity has error:${e.stackTraceToString()}")
                }
                /* when {
                     forceAll -> {
                         entity = entity.setData(movie).copy(dbId = dbMovie?.dbId!!)
                         try {
                             moviesDao.update(entity)
                         } catch (e: Exception) {
                             error("Update error for $entity has error:${e.stackTraceToString()}")
                         }
                     }
                     isTitleChanged(dbMovie, movie) -> {
                         entity = entity.setData(movie).copy(dbId = dbMovie?.dbId!!)
                         try {
                             moviesDao.update(entity)
                         } catch (e: Exception) {
                             error("Update error for $entity has error:${e.stackTraceToString()}")
                         }
                     }

                     isUpdateTimeChanged(dbMovie, movie) -> {
                         entity = entity.setData(movie).copy(dbId = dbMovie?.dbId!!)
                         try {
                             moviesDao.update(entity)
                         } catch (e: Exception) {
                             error("Update error for $entity has error:${e.stackTraceToString()}")
                         }
                     }

                     isGenreChanged(dbMovie, movie) -> {
                         entity = entity.setData(movie).copy(dbId = dbMovie?.dbId!!)
                         try {
                             moviesDao.update(entity)
                         } catch (e: Exception) {
                             error("Update error for $entity has error:${e.stackTraceToString()}")
                         }
                     }

                     dbMovie != null && dbMovie.updated == 0L -> {
                          error("Movie updated is 0L for movie:$dbMovie, has new movie:$movie")
                    }
                    dbMovie == null -> {
                        val newId = moviesDao.getLastId() + 1
                        entity = entity.setData(movie).copy(dbId = newId)
                        try {
                            moviesDao.insert(entity)
                        } catch (e: Exception) {
                            error("Insert error for $entity has error:${e.stackTraceToString()}")
                        }
                    }
                }*/
                if (index % 1000 == 0) {
                    onUpdate(getPercent(index, size))
                }
            }
        }
    }

    private fun isEqualsUrlAndTitle(
        it: MovieUpdate,
        movie: Movie
    ) = it.pageUrl == movie.pageUrl && it.title.equals(movie.title, true)

    private fun isEqualsUrlAndNotTitle(
        it: MovieUpdate,
        movie: Movie
    ) = it.pageUrl == movie.pageUrl && !it.title.equals(movie.title, true)

    private fun isUpdateTimeChanged(dbMovie: MovieUpdate?, movie: Movie) =
        dbMovie != null && dbMovie.updated > 0 && dbMovie.updated < movie.info.updated

    private fun isTitleChanged(dbMovie: MovieUpdate?, movie: Movie) =
        dbMovie != null && dbMovie.title != movie.title

    private fun isGenreChanged(dbMovie: MovieUpdate?, movie: Movie) =
        dbMovie != null && dbMovie.genre != movie.info.genres.joinToString(",")

    override suspend fun checkBaseUrl(): Boolean = try {
        val baseLink = BuildConfig.BASE_LINK
        val page = jsoup.loadPage(
            url = baseLink,
            timeout = 3000
        )
        var link = page.select("ul.tl li")
            .select("a:contains(Фильмы)")
            .attr("href")
        if (link.endsWith("/")) {
            link = link.dropLast(1)
        }
        this.baseUrl = link
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    private fun getPercent(ind: Int, size: Int) =
        ((ind.toDouble() / size.toDouble()) * 100).toInt()
}