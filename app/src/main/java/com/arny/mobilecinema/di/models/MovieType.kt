package com.arny.mobilecinema.di.models

enum class MovieType(val type: Int) {
    CINEMA(1),
    SERIAL(2),
    CINEMA_LOCAL(3),
    SERIAL_LOCAL(4);

    companion object {
        fun fromValue(value: Int): MovieType = values().find { it.type == value } ?: CINEMA
    }
}
