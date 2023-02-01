package com.arny.mobilecinema.data.repository.update

import android.content.Context
import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.data.api.ApiService
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.models.MovieEntityMapper
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.repository.prefs.PrefsConstants
import com.arny.mobilecinema.data.utils.create
import com.arny.mobilecinema.domain.models.AnwapMovie
import com.arny.mobilecinema.domain.repository.UpdateRepository
import java.io.File
import javax.inject.Inject

class UpdateRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val context: Context,
    private val moviesDao: MovieDao,
    private val movieEntityMapper: MovieEntityMapper
) : UpdateRepository {
    override var updateDownloadId: Long = -1L

    override suspend fun downloadUpdate(): File {
        val downloadUrl: String = BuildConfig.update_link
        val file = File(context.filesDir, "update.txt")
        file.delete()
        file.create()
        apiService.downloadFile(file, downloadUrl)
        return file
    }

    override fun getLastUpdate(): String {
        return Prefs.getInstance(context).get<String>(PrefsConstants.LAST_DATA_UPDATE).orEmpty()
    }

    override fun updateMovies(movies: List<AnwapMovie>) {
        if (moviesDao.getCount() == 0) {
            moviesDao.insertAll(movies.map { movieEntityMapper.transform(it) })
        } else {
            for (movie in movies) {
                val movieMinimal = moviesDao.findByPageUrl(movie.pageUrl)
                if (movieMinimal != null) {
                    if (movie.info.updated > movieMinimal.updated) {
                        moviesDao.update(movieEntityMapper.transform(movie))
                    }
                } else {
                    moviesDao.insert(movieEntityMapper.transform(movie))
                }
            }
        }
    }
}