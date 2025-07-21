package com.arny.mobilecinema.presentation.playerview

import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SerialEpisode

/**
 * Состояние UI для экрана плеера.
 * @property isLoading Флаг загрузки данных
 * @property error Сообщение об ошибке, если есть
 * @property movie Текущий фильм/сериал
 * @property currentEpisode Текущая серия (для сериалов)
 * @property playbackPosition Текущая позиция воспроизведения
 * @property duration Длительность медиа
 * @property isPlaying Флаг воспроизведения
 * @property showControls Видимость контролов плеера
 * @property showQualityButton Видимость кнопки выбора качества
 * @property showLanguageButton Видимость кнопки выбора языка
 * @property availableTracks Доступные дорожки
 * @property selectedQualityTrack Выбранное качество
 * @property selectedLanguageTrack Выбранный язык
 * @property resizeModeIndex Режим масштабирования видео
 * @property brightness Уровень яркости (0-30)
 * @property volume Уровень громкости (0-maxVolume)
 * @property boost Уровень усиления громкости (0-maxBoost)
 * @property showVolumeOverlay Видимость оверлея громкости
 * @property showBrightnessOverlay Видимость оверлея яркости
 * @property showNextEpisodeButton Видимость кнопки следующей серии
 * @property showPreviousEpisodeButton Видимость кнопки предыдущей серии
 * @property pipModeRequested Флаг запроса режима PiP
 * @property toastMessage Сообщение для показа в тосте
 * @property navigateBack Флаг навигации назад
 * @property excludeUrls Список URL для исключения при ошибках
 */
data class PlayerUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val movie: Movie? = null,
    val currentEpisode: SerialEpisode? = null,
    val playbackPosition: Long = 0L,
    val duration: Long = 0L,
    val isPlaying: Boolean = false,
    val showControls: Boolean = true,
    val showQualityButton: Boolean = false,
    val showLanguageButton: Boolean = false,
    val availableTracks: Tracks? = null,
    val selectedQualityTrack: TrackSelectionOverride? = null,
    val selectedLanguageTrack: TrackSelectionOverride? = null,
    val resizeModeIndex: Int = 0,
    val brightness: Int = 0,
    val volume: Int = -1,
    val boost: Int = -1,
    val showVolumeOverlay: Boolean = false,
    val showBrightnessOverlay: Boolean = false,
    val showNextEpisodeButton: Boolean = false,
    val showPreviousEpisodeButton: Boolean = false,
    val pipModeRequested: Boolean = false,
    val toastMessage: String? = null,
    val navigateBack: Boolean = false,
    val excludeUrls: Set<String> = emptySet()
) {
    /**
     * Возвращает форматированный заголовок для отображения
     * @param movie Текущий фильм/сериал
     * @return Форматированная строка заголовка
     */
//    fun getFormattedTitle(movie: Movie): String = when (movie.type) {
//        MovieType.SERIAL -> {
//            val season = currentEpisode?.season ?: 0
//            val episode = currentEpisode?.episode ?: 0
//            "${movie.title} (S${season + 1}E${episode + 1})"
//        }
//        else -> movie.title
//    }

    /**
     * Проверяет, является ли текущий контент сериалом
     */
    val isSerial: Boolean get() = movie?.type == MovieType.SERIAL

    /**
     * Возвращает максимальный уровень громкости
     */
    val maxVolume: Int get() = 15 // Можно заменить на получение из AudioManager
}
