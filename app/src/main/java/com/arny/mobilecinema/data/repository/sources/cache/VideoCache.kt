package com.arny.mobilecinema.data.repository.sources.cache

import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.di.models.SerialEpisode
import com.arny.mobilecinema.di.models.SerialSeason

interface VideoCache {
    var currentSeason: SerialSeason?
    var currentEpisode: SerialEpisode?
    fun addToCache(movie: Movie)
    fun getFromCache(title: String): Movie?
    fun searchFromCache(searchText: String): List<Movie>
    fun removeFromCache(movie: Movie): Boolean
}