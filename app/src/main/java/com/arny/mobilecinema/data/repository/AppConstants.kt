package com.arny.mobilecinema.data.repository

object AppConstants {
    const val UPDATE_FILE = "update.txt"
    const val ACTION_UPDATE = "ACTION_UPDATE"
    const val ACTION_UPDATE_STATUS = "ACTION_UPDATE_STATUS"
    const val ACTION_UPDATE_STATUS_STARTED = "ACTION_UPDATE_STATUS_STARTED"
    const val ACTION_UPDATE_STATUS_COMPLETE_SUCCESS = "ACTION_UPDATE_STATUS_COMPLETE_SUCCESS"
    const val ACTION_UPDATE_STATUS_COMPLETE_ERROR = "ACTION_UPDATE_STATUS_COMPLETE_ERROR"
    const val ACTION_CACHE_VIDEO_COMPLETE = "ACTION_CACHE_VIDEO_COMPLETE"
    const val ACTION_CACHE_VIDEO_UPDATE = "ACTION_CACHE_VIDEO_UPDATE"
    const val ACTION_CACHE_MOVIE = "ACTION_CACHE_MOVIE"
    const val ACTION_CACHE_MOVIE_CANCEL = "ACTION_CACHE_MOVIE_CANCEL"
    const val ACTION_CACHE_MOVIE_EXIT = "ACTION_CACHE_MOVIE_EXIT"
    const val ACTION_CACHE_MOVIE_PAUSE = "ACTION_CACHE_MOVIE_PAUSE"
    const val ACTION_CACHE_MOVIE_RESUME = "ACTION_CACHE_MOVIE_RESUME"
    const val ACTION_CACHE_MOVIE_SKIP = "ACTION_CACHE_MOVIE_SKIP"
    const val SERVICE_PARAM_CACHE_URL = "SERVICE_PARAM_CACHE_URL"
    const val SERVICE_PARAM_CACHE_MOVIE_PAGE_URL = "SERVICE_PARAM_CACHE_MOVIE_PAGE_URL"
    const val SERVICE_PARAM_PERCENT = "SERVICE_PARAM_PERCENT"
    const val SERVICE_PARAM_BYTES = "SERVICE_PARAM_BYTES"
    const val SERVICE_PARAM_RESET_CURRENT_DOWNLOADS = "SERVICE_PARAM_RESET_CURRENT_DOWNLOADS"
    const val SERVICE_PARAM_CACHE_TITLE = "SERVICE_PARAM_CACHE_TITLE"
    const val SERVICE_PARAM_CACHE_SEASON = "SERVICE_PARAM_CACHE_SEASON"
    const val SERVICE_PARAM_CACHE_EPISODE = "SERVICE_PARAM_CACHE_EPISODE"
    const val SERVICE_PARAM_FILE = "SERVICE_PARAM_FILE"
    const val SERVICE_PARAM_FORCE_ALL = "SERVICE_PARAM_FORCE_ALL"

    object PARAMS {
        const val DIRECTOR = "director"
        const val ACTOR = "actor"
        const val GENRE = "genre"
    }

    object Order {
        const val NONE = "order_none"
        const val RATINGS = "order_ratings"
        const val TITLE = "order_title"
        const val YEAR_DESC = "order_yearD"
        const val YEAR_ASC = "order_yearA"
    }

    object SearchType {
        const val SEARCH_RESULT = "SEARCH_RESULT"
        const val TITLE = "title"
        const val DIRECTORS = "directors"
        const val ACTORS = "actors"
        const val GENRES = "genres"
        const val COUNTRIES = "countries"
        const val YEARS = "years"
        const val IMDBS = "imdbs"
        const val KPS = "kps"
        const val CINEMA = "cinema"
        const val SERIAL = "serial"
    }

    object Player {
        const val SEASON = "SEASON"
        const val EPISODE = "EPISODE"
    }

    object FRAGMENTS {
        const val RESULTS = "RESULTS"
    }
}