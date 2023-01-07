package com.arny.mobilecinema.data.network.responses

import com.google.gson.annotations.SerializedName

data class AllMoviesData(
    @SerializedName("mock") val mock: List<MockData?> = emptyList()
)