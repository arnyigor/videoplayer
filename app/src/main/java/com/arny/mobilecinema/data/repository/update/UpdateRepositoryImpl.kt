package com.arny.mobilecinema.data.repository.update

import android.content.Context
import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.data.api.ApiService
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.db.models.MovieEntity
import com.arny.mobilecinema.data.models.setData
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.repository.prefs.PrefsConstants
import com.arny.mobilecinema.data.utils.create
import com.arny.mobilecinema.domain.models.AnwapMovie
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
    override var newUpdate: String = ""
    override var updateDownloadId: Long = -1L
    override var lastUpdate: String
        get() = prefs.get<String>(PrefsConstants.LAST_DATA_UPDATE).orEmpty()
        set(value) {
            prefs.put(PrefsConstants.LAST_DATA_UPDATE, value)
        }

    override fun setLastUpdate() {
        lastUpdate = newUpdate
        newUpdate = ""
    }

    override suspend fun downloadUpdate(): File {
        val downloadUrl: String = BuildConfig.update_link
        val file = File(context.filesDir, "update.txt")
        file.delete()
        file.create()
        apiService.downloadFile(file, downloadUrl)
        return file
    }

    override fun updateMovies(movies: List<AnwapMovie>) {
        var entity = MovieEntity()
        if (moviesDao.getCount() == 0) {
            for (movie in movies) {
                entity = entity.setData(movie)
                moviesDao.insert(entity)
            }
        } else {
            val dbList = moviesDao.getAllMinimal()
            movies.forEach { movie ->
                val minimal = dbList.find { it.pageUrl == movie.pageUrl }
                if (minimal != null) {
                    if (minimal.updated < movie.info.updated) {
                        entity = entity.setData(movie)
                        moviesDao.update(entity)
                    }
                } else {
                    entity = entity.setData(movie)
                    moviesDao.insert(entity)
                }
            }
        }
    }
}