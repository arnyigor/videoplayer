package com.arny.mobilecinema.presentation.details

import com.arny.mobilecinema.data.utils.findByGroup
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.SerialSeason

/**
 * Источник воспроизведения: качество («HD»/«SD») и адрес.
 *
 * Подпись для UI строится во фрагменте: здесь нет ресурсов, объект тестируется без Android.
 */
data class PlaySource(val quality: String, val url: String)

/** Результат подготовки запуска (PLAY-01, план v3 §4.2). */
sealed interface PlayStart {
    data class Ready(val source: PlaySource) : PlayStart
    data class Failed(val reason: PlayStartFailure) : PlayStart
}

/**
 * Почему запуск не состоялся. UI обязан показать причину: молчаливый no-op запрещён
 * (план v3 §4.1–4.2).
 */
enum class PlayStartFailure {
    /** Карточка ещё не загружена — нажимать нечего. */
    NO_MOVIE,

    /** У тайтла/выбранной серии нет ни одного непустого адреса. */
    NO_SOURCE,
}

/**
 * Выбор ссылки для кнопки «Смотреть».
 *
 * Раньше порядок источников и выбор серии жили прямо в обработчике клика: при пустом списке
 * ссылок экран молча ничего не делал. Здесь та же логика, но результат типизирован, поэтому
 * вызывающий код не может «промолчать».
 */
object PlayStartResolver {

    const val QUALITY_HD = "HD"
    const val QUALITY_SD = "SD"

    /**
     * @param selectedIndex позиция выбранного источника в списке [sources]; вне диапазона —
     * берётся первый источник, как и раньше в экране деталей.
     */
    fun resolve(
        movie: Movie?,
        seasonPosition: Int = 0,
        episodePosition: Int = 0,
        selectedIndex: Int = 0,
    ): PlayStart {
        if (movie == null) return PlayStart.Failed(PlayStartFailure.NO_MOVIE)
        val available = sources(movie, seasonPosition, episodePosition)
        if (available.isEmpty()) return PlayStart.Failed(PlayStartFailure.NO_SOURCE)
        val source = available.getOrNull(selectedIndex) ?: available.first()
        return PlayStart.Ready(source)
    }

    fun sources(
        movie: Movie,
        seasonPosition: Int = 0,
        episodePosition: Int = 0,
    ): List<PlaySource> = when (movie.type) {
        MovieType.CINEMA -> cinemaSources(movie)
        MovieType.SERIAL -> serialSources(movie, seasonPosition, episodePosition)
        else -> emptyList()
    }

    /** HD-ссылки идут первыми: так выбирал и прежний экран деталей. */
    fun cinemaSources(movie: Movie): List<PlaySource> {
        val data = movie.cinemaUrlData ?: return emptyList()
        val hdUrls = data.hdUrl?.urls.orEmpty().filter { it.isNotBlank() }
        val cinemaUrls = data.cinemaUrl?.urls.orEmpty().filter { it.isNotBlank() }
        return (hdUrls + cinemaUrls).distinct().map { url ->
            PlaySource(if (url in hdUrls) QUALITY_HD else QUALITY_SD, url)
        }
    }

    fun serialSources(
        movie: Movie,
        seasonPosition: Int,
        episodePosition: Int,
    ): List<PlaySource> {
        val episode = episodeAt(movie, seasonPosition, episodePosition) ?: return emptyList()
        return listOf(QUALITY_HD to episode.dash, QUALITY_SD to episode.hls)
            .filter { (_, url) -> url.isNotBlank() }
            .distinctBy { (_, url) -> url }
            .map { (quality, url) -> PlaySource(quality, url) }
    }

    fun episodeAt(
        movie: Movie,
        seasonPosition: Int,
        episodePosition: Int,
    ): SerialEpisode? = sortedSeasons(movie)
        .getOrNull(seasonPosition)
        ?.let { sortedEpisodes(it) }
        ?.getOrNull(episodePosition)

    /** Порядок сезонов и серий в плеере и в выборе на экране деталей должен совпадать. */
    fun sortedSeasons(movie: Movie): List<SerialSeason> = movie.seasons.sortedBy { it.id }

    fun sortedEpisodes(season: SerialSeason): List<SerialEpisode> =
        season.episodes.sortedBy { episodeNumber(it) }

    fun episodeNumber(episode: SerialEpisode): Int =
        findByGroup(episode.episode, "(\\d+)".toRegex(), 1)?.toIntOrNull() ?: 0
}
