package data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnwapEpisodeUrlData(
    @SerialName("file") val `file`: String? = null,
    @SerialName("id")   val id: String? = null,
    @SerialName("start")  val start: String? = null,
    @SerialName("title")  val title: String? = null,
    val urls: List<String> = emptyList()
)