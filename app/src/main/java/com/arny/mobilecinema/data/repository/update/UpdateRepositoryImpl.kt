package com.arny.mobilecinema.data.repository.update

import android.content.Context
import com.arny.mobilecinema.data.api.ApiService
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.db.models.MovieEntity
import com.arny.mobilecinema.data.models.setData
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.repository.prefs.PrefsConstants
import com.arny.mobilecinema.data.utils.create
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.repository.UpdateRepository
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class UpdateRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
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
                    onUpdate(getPersent(ind, size))
                }
            }
        } else {
            val dbList = moviesDao.getUpdateMovies()
            movies.forEachIndexed { index, movie ->
                val dbMovie = dbList.find { it.pageUrl == movie.pageUrl }
                when {
                    dbMovie != null && dbMovie.title != movie.title -> {
                        entity = entity.setData(movie)
                        moviesDao.update(entity)
                    }
                    dbMovie != null && dbMovie.updated < movie.info.updated -> {
                        entity = entity.setData(movie)
                        moviesDao.update(entity)
                    }
                    dbMovie == null -> {
                        entity = entity.setData(movie)
                        moviesDao.insert(entity)
                    }
                }
                if (index % 1000 == 0) {
                    onUpdate(getPersent(index, size))
                }
            }
        }
    }

    private fun getPersent(ind: Int, size: Int) =
        ((ind.toDouble() / size.toDouble()) * 100).toInt()
}