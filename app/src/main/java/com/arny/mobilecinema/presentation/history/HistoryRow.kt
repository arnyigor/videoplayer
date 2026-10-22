package com.arny.mobilecinema.presentation.history

import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.domain.playback.PlaybackProgress

/** Состояние просмотра для строки истории. */
enum class HistoryProgressState {
    /** Данных о прогрессе нет — «Ещё не начинали». */
    NOT_STARTED,

    /** Есть позиция, но эпизод не досмотрен — можно продолжить. */
    IN_PROGRESS,

    /** Политика прогресса отметила просмотр завершённым. */
    COMPLETED,
}

/**
 * Строка истории (HIST-01, план v3 §8): то, что нужно для ответа «на чём я остановился».
 *
 * Экран истории раньше показывал карточки каталога (рейтинг/лайки) без серии и прогресса.
 * Здесь собраны только нужные поля, поэтому маппер проверяется без Android и Room.
 */
data class HistoryRow(
    val dbId: Long,
    val title: String,
    val imagePath: String,
    val year: Int,
    val isSerial: Boolean,
    /** `S02E05` для сериала; null, если каталог не отдал номера сезона/серии. */
    val episodeLabel: String?,
    val progressFraction: Float?,
    val remainingMinutes: Int?,
    /** Время последнего просмотра (мс); 0 — неизвестно. */
    val lastWatchedAt: Long,
    val state: HistoryProgressState,
)

object HistoryRowMapper {

    fun map(movie: ViewMovie, progress: PlaybackProgress?): HistoryRow {
        val isSerial = movie.type == MovieType.SERIAL.value
        return HistoryRow(
            dbId = movie.dbId,
            title = movie.title,
            imagePath = movie.img,
            year = movie.year,
            isSerial = isSerial,
            episodeLabel = episodeLabel(progress),
            progressFraction = fraction(progress),
            remainingMinutes = remainingMinutes(progress),
            lastWatchedAt = progress?.updatedAt ?: 0L,
            state = state(progress),
        )
    }

    /** Метка серии только из реальных номеров: выдумывать `S01E01` нельзя. */
    fun episodeLabel(progress: PlaybackProgress?): String? {
        if (progress == null) return null
        val season = progress.seasonNumber ?: return null
        val episode = progress.episodeNumber ?: return null
        return "S%02dE%02d".format(season, episode)
    }

    fun state(progress: PlaybackProgress?): HistoryProgressState = when {
        progress == null -> HistoryProgressState.NOT_STARTED
        progress.completed -> HistoryProgressState.COMPLETED
        progress.positionMs > 0L -> HistoryProgressState.IN_PROGRESS
        else -> HistoryProgressState.NOT_STARTED
    }

    /** Процент показывается только при известной длительности; иначе — никакой полосы. */
    fun fraction(progress: PlaybackProgress?): Float? {
        val duration = progress?.durationMs?.takeIf { it > 0L } ?: return null
        if (progress.completed) return 1f
        return (progress.positionMs.coerceIn(0L, duration).toDouble() / duration).toFloat()
    }

    /**
     * Остаток округляется вверх: при остатке меньше минуты показываем 1,
     * иначе подпись у конца эпизода была бы «осталось 0 мин».
     */
    fun remainingMinutes(progress: PlaybackProgress?): Int? {
        if (progress == null || progress.completed) return null
        val duration = progress.durationMs?.takeIf { it > 0L } ?: return null
        val left = duration - progress.positionMs
        if (left <= 0L) return null
        return ((left + 59_999L) / 60_000L).toInt()
    }

    /** Текст состояния строки: ресурс + аргумент (остаток), когда он есть. */
    fun statusLabelRes(row: HistoryRow): Int = when (row.state) {
        HistoryProgressState.COMPLETED -> R.string.history_status_completed
        HistoryProgressState.IN_PROGRESS -> when {
            row.remainingMinutes != null -> R.string.mobile_remaining_minutes
            else -> R.string.history_status_in_progress
        }

        HistoryProgressState.NOT_STARTED -> R.string.history_status_not_started
    }
}
