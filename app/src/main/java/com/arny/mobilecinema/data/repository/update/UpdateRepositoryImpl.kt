package com.arny.mobilecinema.data.repository.update

import android.content.Context
import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.data.api.ApiService
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.db.models.MovieEntity
import com.arny.mobilecinema.data.models.setData
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.repository.AppConstants.UPDATE_FILE
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.repository.prefs.PrefsConstants
import com.arny.mobilecinema.data.utils.create
import com.arny.mobilecinema.data.utils.findByGroup
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.repository.UpdateRepository
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
    override var baseUrl: String
        get() = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()
        set(value) {
            prefs.put(PrefsConstants.BASE_URL, value)
        }

    override fun setLastUpdate() {
        lastUpdate = newUpdate
        newUpdate = ""
    }

    override suspend fun checkBaseUrl(): Boolean = try {
        apiService.checkUrl(baseUrl.ifBlank { BuildConfig.base_link })
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    override suspend fun createNewBaseUrl() {
        val url = baseUrl.ifBlank { BuildConfig.base_link }
        val regex = BuildConfig.base_link_regex.toRegex()
        val firstDomain = findByGroup(url, regex, 2).orEmpty()
        val chars = firstDomain.toCharArray()
        if (chars.isNotEmpty()) {
            val alphabet = AppConstants.ALPHABET
            val lastIndex = alphabet.lastIndex
            val newIndex = alphabet.indexOf(chars.getOrNull(1)) + 1
            val newChar = if (newIndex <= lastIndex) {
                alphabet[newIndex]
            } else {
                error("invalid domain")
            }
            val newDomain = firstDomain.substring(0, 1) + newChar
            val newUrl = url.replaceFirst(firstDomain, newDomain)
            baseUrl = newUrl
            if (!checkBaseUrl()) {
                error("invalid domain")
            }
        }
    }

    override suspend fun downloadUpdate(): File {
        val downloadUrl: String = BuildConfig.update_link
        val file = File(context.filesDir, UPDATE_FILE)
        file.delete()
        file.create()
        apiService.downloadFile(file, downloadUrl)
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
                if (dbMovie != null) {
                    if (dbMovie.updated < movie.info.updated) {
                        entity = entity.setData(movie)
                        moviesDao.update(entity)
                    }
                } else {
                    entity = entity.setData(movie)
                    moviesDao.insert(entity)
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