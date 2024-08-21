package com.arny.mobilecinema.domain.repository

import com.arny.mobilecinema.domain.models.Movie

interface JsoupUpdateRepository {
    fun selectMovieByUrl(url: String): Movie?
    fun insertMovie(movie: Movie): Boolean
    fun getMoviesSize(): Int
    fun selectMovieByImg(img: String): Movie?
    fun updateMovie(movie: Movie, dbId: Long): Boolean
}