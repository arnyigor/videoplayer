package com.arny.homecinema.data.models.mocks

import com.google.gson.annotations.SerializedName

data class MockMovie(
	@SerializedName("season")
	val season: Int,
	@SerializedName("episodes")
	val episodes: List<EpisodesItem>
)

data class HlsList(
	@SerializedName("720")
	val jsonMember720: String,
	@SerializedName("480")
	val jsonMember480: String
)

data class EpisodesItem(
	@SerializedName("hlsList")
	val hlsList: HlsList,
	@SerializedName("episode")
	val episode: String,
	@SerializedName("title")
	val title: String
)
