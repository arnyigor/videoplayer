package com.arny.mobilecinema.data.repository

object AppConstants {
    const val UPDATE_FILE = "update.txt"
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
        const val IMDB_ASC = "ratingImdbA"
        const val KP_DESC = "ratingKpD"
        const val KP_ASC = "ratingKpA"
    }

    object Player {
        const val SEASON = "SEASON"
        const val EPISODE = "EPISODE"
    }

    val ALPHABET = arrayOf(
        'a',
        'b',
        'c',
        'd',
        'e',
        'f',
        'g',
        'h',
        'i',
        'j',
        'k',
        'l',
        'm',
        'n',
        'o',
        'p',
        'q',
        'r',
        's',
        't',
        'u',
        'v',
        'w',
        'x',
        'y',
        'z'
    )
}