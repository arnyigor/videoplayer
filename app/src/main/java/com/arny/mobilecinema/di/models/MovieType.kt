package com.arny.mobilecinema.di.models
import com.google.gson.annotations.SerializedName

enum class MovieType(val value: Int) {
    @SerializedName("notype")
    NO_TYPE(0),
    @SerializedName("cinema")
    CINEMA(1),
    @SerializedName("serial")
    SERIAL(2);

    companion object {
        fun fromValue(value: Int): MovieType = values().find { it.value == value } ?: CINEMA
        fun fromStringValue(value: String): MovieType = values().find { it.toString() == value } ?: CINEMA
    }
}
