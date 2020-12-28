package com.arny.homecinema.data.repository.sources.store

import com.arny.homecinema.di.models.Movie

interface StoreProvider {
    fun saveToStore(movie: Movie)
    fun getFromStore(title: String): Movie?
    fun canSaveToStore(): Boolean
    fun searchMovies(searchText: String): List<Movie>
}