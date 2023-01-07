package com.arny.mobilecinema.data.repository.sources.store

import com.arny.mobilecinema.di.models.Movie

interface StoreProvider {
    fun saveToStore(movie: Movie)
    fun getFromStore(title: String): Movie?
    fun canSaveToStore(): Boolean
    fun searchMovies(searchText: String): List<Movie>
    fun allMovies(): List<Movie>
    fun removeFromSaved(movie: Movie)
    fun clearAll()
}