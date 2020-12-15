package com.arny.homecinema.di.models

enum class MovieType(val type: Int) {
    CINEMA(1),
    SERIAL(2);

    companion object {
        fun fromValue(value: Int): MovieType {
            return MovieType.values().find { it.type == value } ?: CINEMA
        }
    }
}
