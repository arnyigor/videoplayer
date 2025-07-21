package com.arny.mobilecinema.domain.repository

/**
 * Репозиторий для сохранения позиции просмотра
 */
interface PositionRepository {
    /**
     * Сохраняет позицию просмотра для фильма
     * @param dbId ID фильма в базе данных
     * @param time Позиция просмотра в миллисекундах
     */
    suspend fun saveCinemaPosition(dbId: Long, time: Long): Result<Unit>

    /**
     * Сохраняет позицию просмотра для сериала
     * @param dbId ID сериала в базе данных
     * @param time Позиция просмотра в миллисекундах
     * @param season Номер сезона
     * @param episode Номер серии
     */
    suspend fun saveSerialPosition(
        dbId: Long,
        time: Long,
        season: Int,
        episode: Int
    ): Result<Unit>
}