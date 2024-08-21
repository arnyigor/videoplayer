package data.models


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnwapSeasonPlaylist(
    @SerialName("playlist")
    val playlist: List<AnwapEpisodeUrlData> = emptyList()
)