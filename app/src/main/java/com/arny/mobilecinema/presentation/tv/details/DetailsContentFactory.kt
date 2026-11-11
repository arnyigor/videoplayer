package com.arny.mobilecinema.presentation.tv.details

import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.playback.PlayableId
import com.arny.mobilecinema.domain.playback.PlayableRef
import com.arny.mobilecinema.domain.playback.PlaybackProgress
import com.arny.mobilecinema.domain.playback.ProgressCompletionPolicy
import com.arny.mobilecinema.domain.playback.ResumeTarget
import com.arny.mobilecinema.domain.playback.playableRefs
import com.arny.mobilecinema.presentation.player.sourceLabel
import kotlin.math.roundToInt

enum class EpisodeStatus { NOT_STARTED, IN_PROGRESS, COMPLETED }

data class SeasonUi(
    val seasonKey: String,
    val seasonIndex: Int,
    val seasonNumber: Int?,
    val episodeCount: Int,
)

/**
 * Карточка эпизода (ТЗ §6.4). Статус считается по прогрессу просмотра, а не по индексам:
 * `progressPercent` есть только у начатых серий с известной длительностью.
 */
data class EpisodeUi(
    val playableId: String,
    val seasonIndex: Int,
    val episodeIndex: Int,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val title: String?,
    val isAvailable: Boolean,
    val status: EpisodeStatus,
    val progressPercent: Int?,
    val isRecommended: Boolean,
)

data class SourceUi(
    val url: String,
    val label: String,
    val isHd: Boolean,
    val playableId: String? = null,
    val seasonIndex: Int = 0,
    val episodeIndex: Int = 0,
)

/** Подпись и координаты главного действия (ТЗ §5/§6): считается resolver'ом, а не экраном. */
data class CtaUi(
    val labelRes: Int,
    val labelArgs: List<Any> = emptyList(),
    val seasonIndex: Int = 0,
    val episodeIndex: Int = 0,
    val playableId: String? = null,
)

/**
 * Чистые вычисления экрана деталей: без Android и Compose, поэтому покрываются unit-тестами.
 * Порядок сезонов и серий берётся из `playableRefs()` (порядок каталога), а не из `season + 1`.
 */
object DetailsContentFactory {

    private val policy = ProgressCompletionPolicy()

    fun playableRefs(movie: Movie): List<PlayableRef> = movie.playableRefs()

    fun seasons(movie: Movie): List<SeasonUi> = movie.playableRefs()
        .groupBy { it.seasonKey.orEmpty() }
        .map { (seasonKey, refs) ->
            val first = refs.first()
            SeasonUi(
                seasonKey = seasonKey,
                seasonIndex = first.seasonIndex,
                seasonNumber = first.seasonNumber,
                episodeCount = refs.size,
            )
        }

    fun episodes(
        movie: Movie,
        seasonIndex: Int,
        progress: Map<String, PlaybackProgress> = emptyMap(),
        recommendedPlayableId: String? = null,
    ): List<EpisodeUi> = movie.playableRefs()
        .asSequence()
        .filter { it.id is PlayableId.Episode && it.seasonIndex == seasonIndex }
        .map { ref -> ref.toEpisodeUi(progress[ref.id.value], ref.id.value == recommendedPlayableId) }
        .toList()

    /**
     * Источники выбранной серии или фильма. DASH идёт первым: на TV он даёт HD,
     * и плеер по умолчанию берёт именно его (как и до перевода деталей на Compose).
     */
    fun sources(movie: Movie, playableId: String?): List<SourceUi> {
        val ref = playableId
            ?.let { id -> movie.playableRefs().firstOrNull { it.id.value == id } }
            ?: movie.playableRefs().firstOrNull()
        return when (movie.type) {
            MovieType.CINEMA -> filmSources(movie)
            MovieType.SERIAL -> episodeSources(ref)
            else -> emptyList()
        }
    }

    /** Keep the sources in the selected season; a resume target from another season is not selected. */
    fun sourcesForSeason(movie: Movie, seasonIndex: Int, recommendedPlayableId: String?): List<SourceUi> {
        if (movie.type != MovieType.SERIAL) return sources(movie, null)
        val available = movie.playableRefs().filter { it.seasonIndex == seasonIndex && it.isAvailable }
        val selected = available.firstOrNull { it.id.value == recommendedPlayableId }
            ?: available.firstOrNull()
            ?: return emptyList()
        return sources(movie, selected.id.value)
    }

    fun cta(movie: Movie, target: ResumeTarget?): CtaUi {
        val ref = recommendedRef(target)
        return when (target) {
            is ResumeTarget.Resume -> if (ref?.id is PlayableId.Episode) {
                CtaUi(
                    labelRes = R.string.tv_continue_s_e,
                    labelArgs = listOf(ref.seasonNumber ?: 1, ref.episodeNumber ?: 1),
                    seasonIndex = ref.seasonIndex,
                    episodeIndex = ref.episodeIndex,
                    playableId = ref.id.value,
                )
            } else {
                CtaUi(
                    labelRes = R.string.tv_continue_at,
                    labelArgs = listOf(formatPosition(target.positionMs)),
                    playableId = ref?.id?.value,
                )
            }

            is ResumeTarget.Start -> if (ref?.id is PlayableId.Episode) {
                CtaUi(
                    labelRes = R.string.tv_play_s_e,
                    labelArgs = listOf(ref.seasonNumber ?: 1, ref.episodeNumber ?: 1),
                    seasonIndex = ref.seasonIndex,
                    episodeIndex = ref.episodeIndex,
                    playableId = ref.id.value,
                )
            } else {
                CtaUi(labelRes = R.string.play, playableId = ref?.id?.value)
            }

            is ResumeTarget.Restart -> CtaUi(
                labelRes = R.string.tv_watch_again,
                seasonIndex = ref?.seasonIndex ?: 0,
                episodeIndex = ref?.episodeIndex ?: 0,
                playableId = ref?.id?.value,
            )

            is ResumeTarget.Unavailable, null -> CtaUi(labelRes = R.string.play)
        }
    }

    fun recommendedRef(target: ResumeTarget?): PlayableRef? = when (target) {
        is ResumeTarget.Resume -> target.ref
        is ResumeTarget.Start -> target.ref
        is ResumeTarget.Restart -> target.ref
        is ResumeTarget.Unavailable, null -> null
    }

    /**
     * Жанры карточки. Словарь (R.array.genres) нужен, потому что часть карточек
     * приходит жанрами одной строкой без запятых — без словаря это один тег.
     */
    fun genres(movie: Movie, limit: Int = 12, dictionary: List<String> = emptyList()): List<String> {
        val items = if (dictionary.isEmpty()) {
            movie.info.genres.splitItems(limit)
        } else {
            com.arny.mobilecinema.data.utils.GenreSplitter.split(movie.info.genres, dictionary)
                .distinct()
                .take(limit)
        }
        return items
    }

    fun actors(movie: Movie, limit: Int = 12): List<String> = movie.info.actors.splitItems(limit)

    fun formatPosition(positionMs: Long): String {
        val totalSec = positionMs / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    private fun List<String>.splitItems(limit: Int): List<String> = asSequence()
        .flatMap { it.split(",").asSequence() }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .take(limit)
        .toList()

    private fun PlayableRef.toEpisodeUi(
        progress: PlaybackProgress?,
        isRecommended: Boolean,
    ): EpisodeUi {
        val completed = progress != null &&
            (progress.completed || policy.isCompleted(progress.positionMs, progress.durationMs))
        val started = !completed && (progress?.positionMs ?: 0L) > 0L
        return EpisodeUi(
            playableId = id.value,
            seasonIndex = seasonIndex,
            episodeIndex = episodeIndex,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            title = episodeTitle,
            isAvailable = isAvailable,
            status = when {
                completed -> EpisodeStatus.COMPLETED
                started -> EpisodeStatus.IN_PROGRESS
                else -> EpisodeStatus.NOT_STARTED
            },
            progressPercent = if (started) {
                progress?.fraction?.let { (it * 100).roundToInt().coerceIn(1, 100) }
            } else {
                null
            },
            isRecommended = isRecommended,
        )
    }

    private fun filmSources(movie: Movie): List<SourceUi> {
        val data = movie.cinemaUrlData ?: return emptyList()
        val hdUrls = data.hdUrl?.urls.orEmpty().filter { it.isNotBlank() }
        val urls = (hdUrls + data.cinemaUrl?.urls.orEmpty().filter { it.isNotBlank() }).distinct()
        val playableId = movie.playableRefs().firstOrNull()?.id?.value
        return urls.map { url -> source(url, isHd = url in hdUrls).copy(playableId = playableId) }
    }

    private fun episodeSources(ref: PlayableRef?): List<SourceUi> {
        if (ref == null) return emptyList()
        val urls = listOf(
            true to ref.dashUrl,
            false to ref.hlsUrl,
        ).mapNotNull { (isHd, url) -> url?.takeIf { it.isNotBlank() }?.let { isHd to it } }
            .distinctBy { (_, url) -> url }
        return urls.map { (isHd, url) ->
            source(url, isHd).copy(
                playableId = ref.id.value,
                seasonIndex = ref.seasonIndex,
                episodeIndex = ref.episodeIndex,
            )
        }
    }

    private fun source(url: String, isHd: Boolean): SourceUi {
        val label = sourceLabel(if (isHd) "HD" else "SD", url)
        return SourceUi(url = url, label = label, isHd = isHd)
    }
}
