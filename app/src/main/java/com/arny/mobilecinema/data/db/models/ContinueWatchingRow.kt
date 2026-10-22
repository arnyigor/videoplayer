package com.arny.mobilecinema.data.db.models

import androidx.room.Embedded

/**
 * Строка «Продолжить просмотр»: прогресс + минимальные поля тайтла.
 */
data class ContinueWatchingRow(
    @Embedded val progress: PlaybackProgressEntity,
    val title: String,
    val img: String,
    val type: Int,
    val year: Int,
)
