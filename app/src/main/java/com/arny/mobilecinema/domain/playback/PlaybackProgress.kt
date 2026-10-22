package com.arny.mobilecinema.domain.playback

/**
 * Прогресс просмотра по конкретному воспроизводимому объекту.
 *
 * [durationMs] может быть null: источник не всегда отдаёт длительность. В этом случае
 * процент не показывается, а завершение не вычисляется.
 */
data class PlaybackProgress(
    val playableId: String,
    val movieDbId: Long,
    val positionMs: Long,
    val durationMs: Long? = null,
    val completed: Boolean = false,
    val updatedAt: Long = 0L,
    val profileId: String = DEFAULT_PROFILE_ID,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
) {
    val fraction: Float?
        get() {
            val duration = durationMs ?: return null
            if (duration <= 0L) return null
            return (positionMs.coerceIn(0L, duration).toDouble() / duration).toFloat()
        }

    companion object {
        const val DEFAULT_PROFILE_ID = "default"
    }
}
