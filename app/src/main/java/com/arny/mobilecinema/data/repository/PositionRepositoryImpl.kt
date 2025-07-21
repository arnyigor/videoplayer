package com.arny.mobilecinema.data.repository

import android.util.Log
import com.arny.mobilecinema.domain.interactors.history.HistoryInteractor
import com.arny.mobilecinema.domain.repository.PositionRepository
import com.arny.mobilecinema.data.models.PositionSaveException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject

class PositionRepositoryImpl @Inject constructor(
    private val historyInteractor: HistoryInteractor
) : PositionRepository {
    private val mutex = Mutex()

    override suspend fun saveCinemaPosition(dbId: Long, time: Long): Result<Unit> {
        return mutex.withLock {
            try {
                val success = historyInteractor.saveCinemaPosition(dbId, time)
                if (success) {
                    Result.success(Unit)
                } else {
                    Result.failure(PositionSaveException("Failed to save cinema position: dbId=$dbId, time=$time"))
                }
            } catch (e: Exception) {
                Timber.Forest.tag("PositionRepo").e(e, "Save cinema error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    override suspend fun saveSerialPosition(
        dbId: Long,
        time: Long,
        season: Int,
        episode: Int
    ): Result<Unit> {
        return mutex.withLock {
            try {
                val success = historyInteractor.saveSerialPosition(
                    movieDbId = dbId,
                    playerSeasonPosition = season,
                    playerEpisodePosition = episode,
                    time = time,
                    currentSeasonPosition = season,
                    currentEpisodePosition = episode
                )
                if (success) {
                    Result.success(Unit)
                } else {
                    Result.failure(
                        PositionSaveException(
                            "Failed to save serial position: " +
                                    "dbId=$dbId, time=$time, season=$season, episode=$episode"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("PositionRepo", "Save serial error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
}