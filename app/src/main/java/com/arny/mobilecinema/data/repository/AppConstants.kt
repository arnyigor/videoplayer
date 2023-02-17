package com.arny.mobilecinema.data.repository

object AppConstants {
    const val UPDATE_FILE = "update.txt"
    const val BASE_URL_FILE = "link.txt"
    const val ACTION_UPDATE = "ACTION_UPDATE"
    const val ACTION_UPDATE_COMPLETE = "ACTION_UPDATE_COMPLETE"
    const val ACTION_CACHE_MOVIE = "ACTION_CACHE_MOVIE"
    const val ACTION_CACHE_MOVIE_CANCEL = "ACTION_CACHE_MOVIE_CANCEL"
    const val SERVICE_PARAM_CACHE_URL = "SERVICE_PARAM_CACHE_URL"
    const val SERVICE_PARAM_CACHE_TITLE = "SERVICE_PARAM_CACHE_TITLE"
    const val SERVICE_PARAM_FILE = "SERVICE_PARAM_FILE"

    object Order {
        const val UPDATED_DESC = "updatedD"
        const val UPDATED_ASC = "updatedA"
        const val YEAR_DESC = "yearD"
        const val YEAR_ASC = "yearA"
        const val IMDB_DESC = "ratingImdbD"
        const val KP_DESC = "ratingKpD"
    }
    object SearchType {
        const val TITLE = "title"
        const val DIRECTORS = "directors"
        const val ACTORS = "actors"
        const val GENRES = "genres"
    }

    object Player {
        const val SEASON = "SEASON"
        const val EPISODE = "EPISODE"
    }
}