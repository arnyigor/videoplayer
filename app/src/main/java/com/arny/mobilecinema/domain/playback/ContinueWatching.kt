package com.arny.mobilecinema.domain.playback

/**
 * Агрегированная карточка «Продолжить просмотр»: одна на тайтл (ТЗ §3.3).
 */
data class ContinueWatchingItem(
    val movieDbId: Long,
    val title: String,
    val posterUrl: String,
    val playableId: String,
    val positionMs: Long,
    val durationMs: Long?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val updatedAt: Long,
) {
    val episodeLabel: String?
        get() {
            val season = seasonNumber ?: return null
            val episode = episodeNumber ?: return null
            return "S%02dE%02d".format(season, episode)
        }

    val progressFraction: Float?
        get() {
            val duration = durationMs?.takeIf { it > 0L } ?: return null
            return (positionMs.coerceIn(0L, duration).toDouble() / duration).toFloat()
        }
}

object ContinueWatchingMapper {
    fun map(
        movieDbId: Long,
        title: String,
        posterUrl: String,
        playableId: String,
        positionMs: Long,
        durationMs: Long?,
        seasonNumber: Int?,
        episodeNumber: Int?,
        updatedAt: Long,
    ): ContinueWatchingItem = ContinueWatchingItem(
        movieDbId = movieDbId,
        title = title,
        posterUrl = posterUrl,
        playableId = playableId,
        positionMs = positionMs,
        durationMs = durationMs?.takeIf { it > 0L },
        seasonNumber = seasonNumber?.takeIf { it > 0 },
        episodeNumber = episodeNumber?.takeIf { it > 0 },
        updatedAt = updatedAt,
    )
}
