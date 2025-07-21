package com.arny.mobilecinema.presentation.playerview

import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.repository.PositionRepository
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ResourceString
import com.arny.mobilecinema.presentation.utils.strings.ThrowableString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Сервис для сохранения позиции просмотра с обработкой ошибок
 * @property repository Репозиторий для работы с позициями
 * @property errorHandler Обработчик ошибок
 * @property coroutineScope Область видимости для корутин
 */
class PositionSaver(
    private val repository: PositionRepository,
    private val errorHandler: (IWrappedString) -> Unit,
    private val coroutineScope: CoroutineScope
) {
    /**
     * Сохраняет позицию просмотра для медиа-контента
     * @param dbId ID медиа в базе данных
     * @param time Позиция просмотра в миллисекундах
     * @param season Номер сезона (для сериалов)
     * @param episode Номер серии (для сериалов)
     * @param movieType Тип медиа-контента
     */
    fun saveMoviePosition(
        dbId: Long?,
        time: Long,
        season: Int,
        episode: Int,
        movieType: MovieType?
    ) {
        coroutineScope.launch {
            if (!isActive) return@launch
            try {
                if (dbId != null) {
                    when (movieType) {
                        MovieType.CINEMA -> saveCinemaPosition(dbId, time)
                        MovieType.SERIAL -> saveSerialPosition(
                            dbId = dbId,
                            time = time,
                            season = season,
                            episode = episode
                        )

                        else -> {}
                    }
                }
            } catch (e: CancellationException) {
                // ignore
            } catch (e: Exception) {
                errorHandler(ThrowableString(e))
            }
        }
    }

    private suspend fun saveCinemaPosition(dbId: Long, time: Long) {
        repository.saveCinemaPosition(dbId, time)
            .onFailure { errorHandler(ResourceString(R.string.movie_save_error)) }
    }

    private suspend fun saveSerialPosition(
        dbId: Long,
        time: Long,
        season: Int,
        episode: Int
    ) {
        repository.saveSerialPosition(dbId, time, season, episode)
            .onFailure { errorHandler(ResourceString(R.string.movie_save_error)) }
    }
}