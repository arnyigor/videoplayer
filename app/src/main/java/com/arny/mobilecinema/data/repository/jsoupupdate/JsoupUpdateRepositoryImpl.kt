package com.arny.mobilecinema.data.repository.jsoupupdate

import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.db.models.MovieEntity
import com.arny.mobilecinema.data.models.MovieMapper
import com.arny.mobilecinema.data.models.setData
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.repository.JsoupUpdateRepository
import javax.inject.Inject

class JsoupUpdateRepositoryImpl @Inject constructor(
    private val movieDao: MovieDao,
    private val movieMapper: MovieMapper,
) : JsoupUpdateRepository {
    override fun selectMovieByUrl(url: String): Movie? {
        return movieDao.getMovie(pageUrl = url)?.let { movieMapper.transform(it) }
    }

    override fun insertMovie(movie: Movie): Boolean {
        return movieDao.insert(MovieEntity().apply { setData(movie) }) > 0L
    }

    override fun getMoviesSize(): Int {
        return movieDao.getCount()
    }

    override fun selectMovieByImg(img: String): Movie? {
        return movieDao.getMovieByImg(img)?.let { movieMapper.transform(it) }
    }

    override fun updateMovie(movie: Movie, dbId: Long): Boolean {
        return movieDao.update(
            MovieEntity().apply { setData(movie) }.copy(
                dbId = dbId
            )
        ) > 0
    }
}