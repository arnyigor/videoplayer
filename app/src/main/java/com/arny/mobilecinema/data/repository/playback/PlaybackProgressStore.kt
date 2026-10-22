package com.arny.mobilecinema.data.repository.playback

import com.arny.mobilecinema.data.db.daos.HistoryDao
import com.arny.mobilecinema.data.db.daos.PlaybackProgressDao
import com.arny.mobilecinema.data.db.models.ContinueWatchingRow
import com.arny.mobilecinema.data.db.models.PlaybackProgressEntity
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.playback.ContinueWatchingItem
import com.arny.mobilecinema.domain.playback.ContinueWatchingMapper
import com.arny.mobilecinema.domain.playback.PlayableId
import com.arny.mobilecinema.domain.playback.PlayableRef
import com.arny.mobilecinema.domain.playback.PlaybackProgress
import com.arny.mobilecinema.domain.playback.ProgressCompletionPolicy
import com.arny.mobilecinema.domain.playback.legacyPlayableRef
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Единственная точка записи и чтения прогресса.
 *
 * Прогресс ключуется `profileId + playableId`, позиция ограничивается длительностью,
 * «просмотрено» вычисляется политикой, а не вызывающим кодом.
 */
class PlaybackProgressStore(
    private val playbackProgressDao: PlaybackProgressDao,
    private val historyDao: HistoryDao,
    private val policy: ProgressCompletionPolicy = ProgressCompletionPolicy(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun progressFor(
        playableId: String,
        profileId: String = PlaybackProgress.DEFAULT_PROFILE_ID,
    ): PlaybackProgress? = withContext(dispatcher) {
        playbackProgressDao.findByPlayable(profileId, playableId)?.toDomain()
    }

    suspend fun progressForMovie(
        movieDbId: Long,
        profileId: String = PlaybackProgress.DEFAULT_PROFILE_ID,
    ): Map<String, PlaybackProgress> = withContext(dispatcher) {
        playbackProgressDao.findAllForMovie(profileId, movieDbId)
            .asSequence()
            .filter { it.playableId.isNotBlank() }
            .associate { it.playableId to it.toDomain() }
    }

    /**
     * Переносит строки старого формата (`legacy:*` или пустой playable_id) на реальные
     * `playableId`. Вызывается при открытии тайтла; повторный вызов безопасен.
     */
    suspend fun migrateLegacy(
        movie: Movie,
        profileId: String = PlaybackProgress.DEFAULT_PROFILE_ID,
    ) = withContext(dispatcher) {
        val rows = playbackProgressDao.findAllForMovie(profileId, movie.dbId)
            .filter { it.playableId.isBlank() || it.playableId.startsWith(LEGACY_PREFIX) }
        if (rows.isEmpty()) return@withContext

        val history = historyDao.getHistory(movie.dbId)
        rows.forEach { row ->
            val ref = movie.legacyPlayableRef(row.season, row.episode) ?: return@forEach
            if (playbackProgressDao.findByPlayable(profileId, ref.id.value) != null) return@forEach

            val legacyPosition = if (
                history != null && history.season == row.season && history.episode == row.episode
            ) {
                history.position
            } else {
                row.positionMs
            }
            playbackProgressDao.updateProgress(
                dbId = row.dbId,
                playableId = ref.id.value,
                season = (ref.seasonNumber ?: 1) - 1,
                episode = (ref.episodeNumber ?: 1) - 1,
                seasonNumber = ref.seasonNumber ?: 0,
                episodeNumber = ref.episodeNumber ?: 0,
                positionMs = legacyPosition.coerceAtLeast(0L),
                durationMs = row.durationMs,
                playedMs = row.playedMs,
                latestTime = row.latestTime,
                completed = row.completed,
                updatedAt = maxOf(row.updatedAt, row.latestTime),
            )
        }
    }

    /**
     * Единственная запись прогресса: позиция продолжения и накопленное время просмотра
     * лежат в одной строке `playableId` (MOB-10B).
     *
     * `playedMsDelta` — время, проигранное с прошлого тика; копится даже когда позицию ещё
     * рано сохранять (нужно персонализации через `SUM(played_ms)`).
     * Возвращает false, если запись не требуется (слишком рано и нет дельты, некорректные данные).
     */
    suspend fun record(
        ref: PlayableRef,
        positionMs: Long,
        durationMs: Long?,
        profileId: String = PlaybackProgress.DEFAULT_PROFILE_ID,
        legacySeasonIndex: Int? = null,
        legacyEpisodeIndex: Int? = null,
        playedMsDelta: Long = 0L,
    ): Boolean = withContext(dispatcher + NonCancellable) {
        val duration = durationMs?.takeIf { it > 0L }
        val completed = policy.isCompleted(positionMs, duration)
        val persistPosition = completed || policy.shouldPersist(positionMs, duration)
        val playedDelta = playedMsDelta.coerceAtLeast(0L)
        if (!persistPosition && playedDelta == 0L) return@withContext false

        val position = if (duration != null) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        val now = clock()
        val seasonNumber = ref.seasonNumber ?: 0
        val episodeNumber = ref.episodeNumber ?: 0
        val seasonIndex = if (ref.id is PlayableId.Episode) (seasonNumber - 1).coerceAtLeast(0) else 0
        val episodeIndex = if (ref.id is PlayableId.Episode) (episodeNumber - 1).coerceAtLeast(0) else 0

        val existing = playbackProgressDao.findByPlayable(profileId, ref.id.value)
        if (existing != null) {
            playbackProgressDao.updateProgress(
                dbId = existing.dbId,
                playableId = ref.id.value,
                season = seasonIndex,
                episode = episodeIndex,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                positionMs = if (persistPosition) position else existing.positionMs,
                durationMs = if (persistPosition) duration ?: 0L else existing.durationMs,
                playedMs = existing.playedMs + playedDelta,
                latestTime = now,
                completed = if (persistPosition) completed else existing.completed,
                updatedAt = if (persistPosition) now else existing.updatedAt,
            )
            return@withContext true
        }

        val legacy = if (legacySeasonIndex != null && legacyEpisodeIndex != null) {
            playbackProgressDao.findLegacy(profileId, ref.movieDbId, legacySeasonIndex, legacyEpisodeIndex)
        } else {
            null
        }
        if (legacy != null) {
            playbackProgressDao.updateProgress(
                dbId = legacy.dbId,
                playableId = ref.id.value,
                season = seasonIndex,
                episode = episodeIndex,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                positionMs = if (persistPosition) position else legacy.positionMs,
                durationMs = if (persistPosition) duration ?: 0L else legacy.durationMs,
                playedMs = legacy.playedMs + playedDelta,
                latestTime = now,
                completed = if (persistPosition) completed else legacy.completed,
                updatedAt = if (persistPosition) now else legacy.updatedAt,
            )
            return@withContext true
        }

        playbackProgressDao.insert(
            PlaybackProgressEntity(
                movieDbId = ref.movieDbId,
                season = seasonIndex,
                episode = episodeIndex,
                playedMs = playedDelta,
                latestTime = now,
                playableId = ref.id.value,
                profileId = profileId,
                positionMs = if (persistPosition) position else 0L,
                durationMs = if (persistPosition) duration ?: 0L else 0L,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                completed = if (persistPosition) completed else false,
                updatedAt = if (persistPosition) now else 0L,
            )
        )
        true
    }

    fun observeContinueWatching(
        profileId: String = PlaybackProgress.DEFAULT_PROFILE_ID,
        minPositionMs: Long = 15_000L,
        limit: Int = 20,
    ): Flow<List<ContinueWatchingItem>> =
        playbackProgressDao.observeContinueWatching(profileId, minPositionMs, limit)
            .map { rows -> rows.mapNotNull { it.toItem() } }

    private fun ContinueWatchingRow.toItem(): ContinueWatchingItem? {
        if (progress.playableId.isBlank()) return null
        return ContinueWatchingMapper.map(
            movieDbId = progress.movieDbId,
            title = title,
            posterUrl = img,
            playableId = progress.playableId,
            positionMs = progress.positionMs,
            durationMs = progress.durationMs,
            seasonNumber = progress.seasonNumber,
            episodeNumber = progress.episodeNumber,
            updatedAt = progress.updatedAt,
        )
    }

    private companion object {
        const val LEGACY_PREFIX = "legacy:"
    }
}
