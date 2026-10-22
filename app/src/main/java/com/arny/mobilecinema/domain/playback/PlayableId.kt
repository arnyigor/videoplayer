package com.arny.mobilecinema.domain.playback

/**
 * Стабильная логическая идентичность воспроизводимого объекта.
 *
 * Не является URL: подписанная ссылка на поток живёт отдельно и может меняться.
 * Используется как ключ прогресса (профиль + playableId) и как mediaId в плеере.
 */
sealed interface PlayableId {
    val value: String

    data class Film(val movieDbId: Long) : PlayableId {
        override val value: String get() = "film:$movieDbId"
    }

    data class Episode(
        val movieDbId: Long,
        val seasonKey: String,
        val episodeKey: String,
    ) : PlayableId {
        override val value: String get() = "ep:$movieDbId:$seasonKey:$episodeKey"
    }

    companion object {
        fun parse(raw: String): PlayableId? {
            if (raw.isBlank()) return null
            val parts = raw.split(':')
            return when {
                parts.size == 2 && parts[0] == "film" ->
                    parts[1].toLongOrNull()?.let(::Film)

                parts.size == 4 && parts[0] == "ep" ->
                    parts[1].toLongOrNull()?.let { Episode(it, parts[2], parts[3]) }

                else -> null
            }
        }
    }
}
