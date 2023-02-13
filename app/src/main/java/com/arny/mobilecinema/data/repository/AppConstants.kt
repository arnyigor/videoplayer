package com.arny.mobilecinema.data.repository

object AppConstants {
    const val UPDATE_FILE = "update.txt"
    const val ACTION_UPDATE_COMPLETE = "ACTION_UPDATE_COMPLETE"

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