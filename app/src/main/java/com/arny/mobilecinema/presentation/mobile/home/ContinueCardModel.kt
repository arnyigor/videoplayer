package com.arny.mobilecinema.presentation.mobile.home

import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.playback.ContinueWatchingItem

/**
 * Данные карточки «Продолжить просмотр» для мобильной главной (ТЗ ч. II §20.2, план MOB-09).
 *
 * Метки считаются здесь, а не в Compose: одна запись на сериал приходит из
 * `PlaybackProgressStore.observeContinueWatching()`, и её надо показать без обращения к БД.
 */
data class ContinueCardModel(
    val movieDbId: Long,
    val title: String,
    val posterUrl: String,
    /** `S02E05` для сериала; null для фильма. */
    val episodeLabel: String?,
    val progressFraction: Float?,
    /** id строки «осталось N мин» с её аргументом; null, если длительность неизвестна. */
    val remaining: RemainingLabel?,
)

data class RemainingLabel(val resId: Int, val minutes: Int)

object ContinueCardMapper {

    fun toCard(item: ContinueWatchingItem): ContinueCardModel = ContinueCardModel(
        movieDbId = item.movieDbId,
        title = item.title,
        posterUrl = item.posterUrl,
        episodeLabel = episodeLabel(item),
        progressFraction = fraction(item),
        remaining = remaining(item),
    )

    /** Метка серии только когда каталог отдал номера: выдумывать S01E01 нельзя. */
    fun episodeLabel(item: ContinueWatchingItem): String? {
        val season = item.seasonNumber ?: return null
        val episode = item.episodeNumber ?: return null
        return "S%02dE%02d".format(season, episode)
    }

    fun fraction(item: ContinueWatchingItem): Float? {
        val duration = item.durationMs ?: return null
        if (duration <= 0L) return null
        return (item.positionMs.coerceIn(0L, duration).toDouble() / duration).toFloat()
    }

    /**
     * Остаток округляется вверх: при остатке меньше минуты показываем 1,
     * иначе карточка у конца эпизода писала бы «осталось 0 мин».
     */
    fun remaining(item: ContinueWatchingItem): RemainingLabel? {
        val duration = item.durationMs ?: return null
        if (duration <= 0L) return null
        val leftMs = duration - item.positionMs
        if (leftMs <= 0L) return null
        val minutes = ((leftMs + 59_999L) / 60_000L).toInt()
        return RemainingLabel(R.string.mobile_remaining_minutes, minutes)
    }
}
