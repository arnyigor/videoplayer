package com.arny.homecinema.data.models

import com.google.gson.annotations.SerializedName

data class M3u8Response(
	@SerializedName("hls")
	val hls: String
)

