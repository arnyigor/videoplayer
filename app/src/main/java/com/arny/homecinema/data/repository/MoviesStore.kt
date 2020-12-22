package com.arny.homecinema.data.repository

import com.arny.homecinema.di.models.Movie
import com.arny.homecinema.di.models.SerialEpisode
import com.arny.homecinema.di.models.SerialSeason

object MoviesStore {
    val movies = mutableListOf<Movie>()
    var currentSeason: SerialSeason? = null
    var currentEpisode: SerialEpisode? = null
}