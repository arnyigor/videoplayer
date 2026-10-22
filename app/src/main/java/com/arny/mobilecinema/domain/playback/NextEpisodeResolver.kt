package com.arny.mobilecinema.domain.playback

import com.arny.mobilecinema.domain.models.Movie

sealed interface ResumeTarget {
    data class Resume(val ref: PlayableRef, val positionMs: Long) : ResumeTarget
    data class Start(val ref: PlayableRef) : ResumeTarget
    data class Restart(val ref: PlayableRef) : ResumeTarget
    data class Unavailable(val reason: String) : ResumeTarget
}

/**
 * Определение актуальной серии по порядку каталога (ТЗ §8.2).
 *
 * Правила:
 * 1. незавершённый активный прогресс — продолжаем его;
 * 2. иначе — следующая доступная после последней завершённой;
 * 3. если завершено всё — предлагаем пересмотр с первой доступной;
 * 4. если истории нет — первая доступная;
 * 5. недоступная следующая серия не подменяется первой серией другого сезона.
 *
 * Нумерация seasons/episodes не используется арифметически: порядок берётся из каталога.
 */
class NextEpisodeResolver(
    private val policy: ProgressCompletionPolicy = ProgressCompletionPolicy(),
) {
    fun ordered(movie: Movie): List<PlayableRef> = movie.playableRefs()

    fun resumeTarget(
        movie: Movie,
        progress: Map<String, PlaybackProgress>,
    ): ResumeTarget {
        val ordered = ordered(movie)
        if (ordered.isEmpty()) return ResumeTarget.Unavailable("no playable items")

        val available = ordered.filter { it.isAvailable }
        if (available.isEmpty()) return ResumeTarget.Unavailable("no available sources")

        val availableById = available.associateBy { it.id.value }
        val movieProgress = progress.values.filter { it.movieDbId == movie.dbId }

        // Завершённость может быть ещё не записана в БД: позиция у конца эпизода уже означает конец.
        val effectivelyCompleted = movieProgress.filter {
            it.completed || policy.isCompleted(it.positionMs, it.durationMs)
        }
        val active = movieProgress
            .filterNot { it in effectivelyCompleted }
            .filter { it.positionMs > 0L && availableById.containsKey(it.playableId) }
            .maxByOrNull { it.updatedAt }

        if (active != null) {
            val ref = availableById.getValue(active.playableId)
            val position = policy.resumePosition(active.positionMs, active.durationMs)
            return if (position > 0L) ResumeTarget.Resume(ref, position) else ResumeTarget.Start(ref)
        }

        val completedIds = effectivelyCompleted.mapTo(mutableSetOf()) { it.playableId }
        if (completedIds.isEmpty()) return ResumeTarget.Start(available.first())

        val lastCompletedIndex = ordered.indexOfLast { it.id.value in completedIds }
        if (lastCompletedIndex < 0) return ResumeTarget.Start(available.first())

        if (lastCompletedIndex >= ordered.lastIndex) {
            return ResumeTarget.Restart(available.first())
        }
        val next = ordered.drop(lastCompletedIndex + 1).firstOrNull { it.isAvailable }
        return next?.let { ResumeTarget.Start(it) }
            ?: ResumeTarget.Unavailable("next episode is unavailable")
    }

    fun next(movie: Movie, current: PlayableId): PlayableRef? {
        val ordered = ordered(movie)
        val index = ordered.indexOfFirst { it.id == current }
        if (index < 0) return null
        return ordered.drop(index + 1).firstOrNull { it.isAvailable }
    }

    fun previous(movie: Movie, current: PlayableId): PlayableRef? {
        val ordered = ordered(movie)
        val index = ordered.indexOfFirst { it.id == current }
        if (index <= 0) return null
        return ordered.take(index).lastOrNull { it.isAvailable }
    }
}
