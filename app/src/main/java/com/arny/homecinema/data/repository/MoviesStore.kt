package com.arny.homecinema.data.repository

import com.arny.homecinema.di.models.Movie

object MoviesStore {
    val movies = mutableListOf<Movie>()
}