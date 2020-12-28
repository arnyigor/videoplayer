package com.arny.homecinema.data.repository.sources.cache

import com.arny.homecinema.di.models.Movie
import com.arny.homecinema.di.models.SerialEpisode
import com.arny.homecinema.di.models.SerialSeason
import javax.inject.Inject

class VideoCacheImpl @Inject constructor() : VideoCache {
    private val movies = mutableListOf<Movie>()
    override var currentSeason: SerialSeason? = null
    override var currentEpisode: SerialEpisode? = null
    override fun addToCache(movie: Movie) {
        val cachedIndex = movies.indexOfFirst { it.title == movie.title }
        if (cachedIndex >= 0) {
            movies[cachedIndex] = movie
        } else {
            if (hasVideoData(movie)) {
                movies.add(movie)
            }
        }
    }

    override fun getFromCache(title: String): Movie? {
        return movies.find { it.title == title }
    }

    override fun searchFromCache(searchText: String): List<Movie> {
        return movies.filter { it.title.contains(searchText, true) }
    }

    private fun hasVideoData(movie: Movie): Boolean {
        if (movie.title.isBlank()) return false
        if (movie.video?.videoUrl.isNullOrBlank()) return false
        if (movie.video?.hlsList?.entries.isNullOrEmpty()) return false
        return true
    }
}