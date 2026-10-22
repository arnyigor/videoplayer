package com.arny.mobilecinema.domain.playback

import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.SerialSeason

/**
 * Воспроизводимый объект каталога с метаданными, достаточными для UI и резолвера.
 *
 * @param seasonNumber отображаемый номер сезона из каталога (не индекс массива), если известен.
 * @param episodeNumber номер серии, распознанный из строки каталога, если известен.
 * @param isAvailable есть хотя бы один пригодный источник (HLS/DASH).
 */
data class PlayableRef(
    val id: PlayableId,
    val movieDbId: Long,
    val titleName: String,
    val seasonKey: String? = null,
    val episodeKey: String? = null,
    /** Индекс сезона в исходном массиве каталога (для навигации по старым аргументам). */
    val seasonIndex: Int = 0,
    /** Индекс серии в исходном массиве сезона. */
    val episodeIndex: Int = 0,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val durationMs: Long? = null,
    val hlsUrl: String? = null,
    val dashUrl: String? = null,
    val posterUrl: String? = null,
    val isAvailable: Boolean = false,
)

private val episodeNumberRegex = Regex("(\\d+)")

fun SerialEpisode.episodeNumber(): Int? =
    episodeNumberRegex.find(episode)?.groupValues?.getOrNull(1)?.toIntOrNull()

/**
 * Порядок каталога: сезоны по номеру, серии по распознанному номеру.
 * Индексы массивов используются только как fallback-ключи, когда каталог не отдал id.
 */
fun Movie.playableRefs(): List<PlayableRef> = when (type) {
    MovieType.CINEMA -> listOf(filmPlayable())
    MovieType.SERIAL -> orderedSeasons().flatMap { season -> season.refs(this) }
    else -> emptyList()
}

/**
 * Ссылка на эпизод по старым координатам `(seasonIndex, episodeIndex)`.
 *
 * Индексы — позиции в отсортированных списках сезонов/серий, как их сохранял плеер до
 * появления `playableId`. Нужна только для ленивой миграции старого прогресса.
 */
/**
 * Плоский индекс эпизода в [playableRefs] по координатам каталога.
 */
fun Movie.flatPlayableIndex(seasonIndex: Int, episodeIndex: Int): Int? {
    if (type == MovieType.CINEMA) return 0
    if (type != MovieType.SERIAL) return null
    val seasons = orderedSeasons()
    val season = seasons.getOrNull(seasonIndex) ?: return null
    if (episodeIndex !in season.episodes.indices) return null
    return seasons.take(seasonIndex).sumOf { it.episodes.size } + episodeIndex
}

fun Movie.legacyPlayableRef(seasonIndex: Int, episodeIndex: Int): PlayableRef? {
    if (type != MovieType.SERIAL) return playableRefs().firstOrNull()
    val season = orderedSeasons().getOrNull(seasonIndex) ?: return null
    val episode = season.episodes.getOrNull(episodeIndex) ?: return null
    val episodeKey = episode.episode.id.takeIf { it > 0 }?.toString() ?: "i${episode.index}"
    return season.refs(this).firstOrNull { it.episodeKey == episodeKey }
}

private fun Movie.filmPlayable(): PlayableRef {
    val urls = cinemaUrlData
        ?.let { it.hdUrl?.urls.orEmpty() + it.cinemaUrl?.urls.orEmpty() }
        .orEmpty()
        .filter { it.isNotBlank() }
        .distinct()
    return PlayableRef(
        id = PlayableId.Film(dbId),
        movieDbId = dbId,
        titleName = title,
        durationMs = info.durationSec.takeIf { it > 0 }?.let { it * 1_000L },
        hlsUrl = urls.firstOrNull(),
        posterUrl = img.takeIf { it.isNotBlank() },
        isAvailable = urls.isNotEmpty(),
    )
}

private data class OrderedEpisode(
    val episode: SerialEpisode,
    val index: Int,
    val number: Int?,
)

private data class OrderedSeason(
    val season: SerialSeason,
    val index: Int,
    val episodes: List<OrderedEpisode>,
) {
    fun refs(movie: Movie): List<PlayableRef> {
        val seasonKey = season.id?.toString() ?: "i$index"
        return episodes.map { ordered ->
            val episode = ordered.episode
            val episodeKey = episode.id.takeIf { it > 0 }?.toString() ?: "i${ordered.index}"
            PlayableRef(
                id = PlayableId.Episode(movie.dbId, seasonKey, episodeKey),
                movieDbId = movie.dbId,
                titleName = movie.title,
                seasonKey = seasonKey,
                episodeKey = episodeKey,
                seasonIndex = index,
                episodeIndex = ordered.index,
                seasonNumber = season.id,
                episodeNumber = ordered.number,
                episodeTitle = episode.title.takeIf { it.isNotBlank() },
                hlsUrl = episode.hls.takeIf { it.isNotBlank() },
                dashUrl = episode.dash.takeIf { it.isNotBlank() },
                posterUrl = episode.poster.takeIf { it.isNotBlank() },
                isAvailable = episode.hls.isNotBlank() || episode.dash.isNotBlank(),
            )
        }
    }
}

private fun Movie.orderedSeasons(): List<OrderedSeason> = seasons
    .mapIndexed { index, season ->
        val episodes = season.episodes
            .mapIndexed { episodeIndex, episode ->
                OrderedEpisode(episode, episodeIndex, episode.episodeNumber())
            }
            .sortedWith(compareBy({ it.number ?: Int.MAX_VALUE }, { it.index }))
        OrderedSeason(season, index, episodes)
    }
    .sortedWith(compareBy({ it.season.id ?: Int.MAX_VALUE }, { it.index }))
