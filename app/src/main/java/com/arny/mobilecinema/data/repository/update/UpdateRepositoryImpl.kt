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

    override fun updateMovies(movies: List<Movie>, onUpdate: (ind: Int) -> Unit) {
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
                val dbMovie = dbList.find { it.pageUrl == movie.pageUrl }
                when {
                    isTitleChanged(dbMovie, movie) -> {
                        entity = entity.setData(movie)
                        moviesDao.update(entity)
                    }

                    isUpdateTimeChanged(dbMovie, movie) -> {
                        entity = entity.setData(movie)
                        moviesDao.update(entity)
                    }

                    dbMovie == null || dbMovie.updated == 0L -> {
                        entity = entity.setData(movie)
                        moviesDao.insert(entity)
                    }
                }
                if (index % 1000 == 0) {
                    onUpdate(getPercent(index, size))
                }
            }
        }
    }

    private fun isUpdateTimeChanged(
        dbMovie: MovieUpdate?,
        movie: Movie
    ) = dbMovie != null && dbMovie.updated > 0 && dbMovie.updated < movie.info.updated

    private fun isTitleChanged(
        dbMovie: MovieUpdate?,
        movie: Movie
    ) = dbMovie != null && dbMovie.title != movie.title

    override suspend fun checkBaseUrl(): Boolean = try {
        val baseLink = BuildConfig.BASE_LINK
        val page = jsoup.loadPage(baseLink)
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