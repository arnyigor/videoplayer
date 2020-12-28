package com.arny.homecinema.data.repository.sources.cache

import com.arny.homecinema.di.models.Movie
import com.arny.homecinema.di.models.SerialEpisode
import com.arny.homecinema.di.models.SerialSeason

interface VideoCache {
    var currentSeason: SerialSeason?
    var currentEpisode: SerialEpisode?
    fun addToCache(movie: Movie)
    fun getFromCache(title: String): Movie?
    fun searchFromCache(searchText: String): List<Movie>
}