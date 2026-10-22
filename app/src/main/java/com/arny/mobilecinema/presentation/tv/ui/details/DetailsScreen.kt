package com.arny.mobilecinema.presentation.tv.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.utils.getWithDomain
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieInfo
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.playback.PlaybackProgress
import com.arny.mobilecinema.domain.playback.ResumeTarget
import com.arny.mobilecinema.presentation.tv.details.CtaUi
import com.arny.mobilecinema.presentation.tv.details.DetailsContentFactory
import com.arny.mobilecinema.presentation.tv.details.EpisodeStatus
import com.arny.mobilecinema.presentation.tv.details.EpisodeUi
import com.arny.mobilecinema.presentation.tv.details.SeasonUi
import com.arny.mobilecinema.presentation.tv.details.SourceUi
import com.arny.mobilecinema.presentation.tv.ui.components.SeasonTab
import com.arny.mobilecinema.presentation.tv.ui.components.SeasonTabs
import com.arny.mobilecinema.presentation.tv.ui.components.SectionHeader
import com.arny.mobilecinema.presentation.tv.ui.components.TvFilterChip
import com.arny.mobilecinema.presentation.tv.ui.theme.TvColors
import com.arny.mobilecinema.presentation.tv.ui.theme.TvDimens
import com.arny.mobilecinema.presentation.tv.ui.theme.TvTheme

/**
 * Детали фильма и сериала (ТЗ §5–§6): backdrop, метаданные, CTA из resolver'а,
 * сезоны/серии со статусами, источники, жанры и актёры.
 *
 * CTA — первый фокусируемый элемент экрана, как требует §6.3.
 */
@Composable
fun DetailsScreen(
    movie: Movie,
    isFavorite: Boolean,
    resumeTarget: ResumeTarget?,
    progress: Map<String, PlaybackProgress>,
    selectedSeasonIndex: Int,
    cta: CtaUi,
    isUpdating: Boolean,
    updatePercent: Int,
    onPlay: () -> Unit,
    onPlayEpisode: (EpisodeUi) -> Unit,
    onPlaySource: (SourceUi) -> Unit,
    onToggleFavorite: () -> Unit,
    onUpdate: () -> Unit,
    onCancelUpdate: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onSearch: (query: String, searchType: String) -> Unit,
    modifier: Modifier = Modifier,
    imageBaseUrl: String = "",
    listState: LazyListState = rememberLazyListState(),
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(movie.dbId) {
        runCatching { focusRequester.requestFocus() }
    }

    val seasons = remember(movie) { DetailsContentFactory.seasons(movie) }
    val episodes = remember(movie, selectedSeasonIndex, progress, resumeTarget) {
        DetailsContentFactory.episodes(
            movie = movie,
            seasonIndex = selectedSeasonIndex,
            progress = progress,
            recommendedPlayableId = DetailsContentFactory.recommendedRef(resumeTarget)?.id?.value,
        )
    }
    val sources = remember(movie, episodes, cta) {
        DetailsContentFactory.sources(movie, cta.playableId)
    }
    val genres = remember(movie) { DetailsContentFactory.genres(movie) }
    val actors = remember(movie) { DetailsContentFactory.actors(movie) }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = TvDimens.SectionGap),
    ) {
        item(key = "hero") {
            DetailsHero(movie = movie, imageBaseUrl = imageBaseUrl)
        }

        item(key = "actions") {
            Row(
                modifier = Modifier.padding(
                    horizontal = TvDimens.ScreenHorizontal,
                    vertical = 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(TvDimens.CardGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier.focusRequester(focusRequester),
                ) {
                    Text(text = stringResource(cta.labelRes, *cta.labelArgs.toTypedArray()))
                }
                TvFilterChip(
                    label = stringResource(
                        if (isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites
                    ),
                    selected = isFavorite,
                    onClick = onToggleFavorite,
                )
                TvFilterChip(
                    label = updateLabel(isUpdating, updatePercent),
                    selected = isUpdating,
                    onClick = if (isUpdating) onCancelUpdate else onUpdate,
                )
            }
        }

        if (movie.type == MovieType.SERIAL && seasons.isNotEmpty()) {
            item(key = "seasons") {
                SeasonTabs(
                    seasons = seasons.map { SeasonTab(it.seasonIndex, seasonLabel(it)) },
                    selectedId = selectedSeasonIndex,
                    onSelect = onSelectSeason,
                )
            }
            item(key = "episodes") {
                Column {
                    SectionHeader(title = stringResource(R.string.tv_episodes_title))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = TvDimens.ScreenHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(TvDimens.CardGap),
                    ) {
                        items(items = episodes, key = { it.playableId }) { episode ->
                            EpisodeCard(
                                episode = episode,
                                imageUrl = episodeImage(movie, episode, imageBaseUrl),
                                onClick = { onPlayEpisode(episode) },
                            )
                        }
                    }
                }
            }
        }

        if (sources.isNotEmpty()) {
            item(key = "sources") {
                Column {
                    SectionHeader(title = stringResource(R.string.tv_sources_title))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = TvDimens.ScreenHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(TvDimens.CardGap),
                    ) {
                        items(items = sources, key = { it.url }) { source ->
                            TvFilterChip(
                                label = source.label,
                                selected = false,
                                onClick = { onPlaySource(source) },
                            )
                        }
                    }
                }
            }
        }

        if (genres.isNotEmpty()) {
            item(key = "genres") {
                TagRow(
                    title = stringResource(R.string.genres),
                    tags = genres,
                    searchType = AppConstants.SearchType.GENRES,
                    onSearch = onSearch,
                )
            }
        }

        if (actors.isNotEmpty()) {
            item(key = "actors") {
                TagRow(
                    title = stringResource(R.string.actors),
                    tags = actors,
                    searchType = AppConstants.SearchType.ACTORS,
                    onSearch = onSearch,
                )
            }
        }
    }
}

@Composable
private fun DetailsHero(movie: Movie, imageBaseUrl: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(340.dp),
    ) {
        AsyncImage(
            model = movie.img.takeIf { it.isNotBlank() }?.getWithDomain(imageBaseUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .background(TvColors.Surface),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0x99000000), Color(0xF20D0F13)),
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = TvDimens.ScreenHorizontal, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = movie.title,
                style = MaterialTheme.typography.headlineMedium,
                color = TvColors.OnBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = heroMeta(movie)
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val description = movie.info.description
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TvColors.TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: EpisodeUi,
    imageUrl: String?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(TvDimens.EpisodeWidth),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(TvDimens.EpisodeHeight),
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TvColors.Surface),
                )
                val label = episodeLabel(episode)
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xB3000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (episode.isRecommended) TvColors.Accent else TvColors.OnBackground,
                    )
                }
                val progressPercent = episode.progressPercent
                if (episode.status == EpisodeStatus.IN_PROGRESS && progressPercent != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color(0x66000000))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progressPercent / 100f)
                                .height(4.dp)
                                .background(TvColors.Accent)
                        )
                    }
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = episode.title ?: episodeLabel(episode),
                    style = MaterialTheme.typography.bodySmall,
                    color = TvColors.OnBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = episodeStatusText(episode),
                    style = MaterialTheme.typography.labelSmall,
                    color = episodeStatusColor(episode),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TagRow(
    title: String,
    tags: List<String>,
    searchType: String,
    onSearch: (String, String) -> Unit,
) {
    Column {
        SectionHeader(title = title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = TvDimens.ScreenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(TvDimens.CardGap),
        ) {
            items(items = tags, key = { it }) { tag ->
                TvFilterChip(
                    label = tag,
                    selected = false,
                    onClick = { onSearch(tag, searchType) },
                )
            }
        }
    }
}

@Composable
private fun seasonLabel(season: SeasonUi): String = season.seasonNumber
    ?.let { stringResource(R.string.tv_season_number, it) }
    ?: stringResource(R.string.tv_season_number, season.seasonIndex + 1)

@Composable
private fun episodeLabel(episode: EpisodeUi): String =
    if (episode.seasonNumber != null && episode.episodeNumber != null) {
        "S%02dE%02d".format(episode.seasonNumber, episode.episodeNumber)
    } else {
        stringResource(R.string.tv_episode_label, (episode.episodeNumber ?: episode.episodeIndex + 1).toString())
    }

@Composable
private fun episodeStatusText(episode: EpisodeUi): String = when {
    !episode.isAvailable -> stringResource(R.string.tv_episode_unavailable)
    episode.status == EpisodeStatus.COMPLETED -> stringResource(R.string.tv_episode_watched)
    episode.status == EpisodeStatus.IN_PROGRESS -> "${episode.progressPercent ?: 0}%"
    else -> ""
}

@Composable
private fun episodeStatusColor(episode: EpisodeUi): Color = when {
    !episode.isAvailable -> TvColors.TextSecondary
    episode.status == EpisodeStatus.COMPLETED -> TvColors.Accent
    else -> TvColors.TextSecondary
}

@Composable
private fun updateLabel(isUpdating: Boolean, updatePercent: Int): String =
    if (isUpdating) {
        val percent = updatePercent.takeIf { it in 0..100 }?.let { " $it%" }.orEmpty()
        stringResource(R.string.updating_data) + percent
    } else {
        stringResource(R.string.update_data)
    }

@Composable
private fun episodeImage(movie: Movie, episode: EpisodeUi, imageBaseUrl: String): String? {
    val poster = DetailsContentFactory.playableRefs(movie)
        .firstOrNull { it.id.value == episode.playableId }
        ?.posterUrl
    return (poster ?: movie.img).takeIf { it.isNotBlank() }?.getWithDomain(imageBaseUrl)
}

private fun heroMeta(movie: Movie): String = buildList {
    val info = movie.info
    if (info.year > 0) add(info.year.toString())
    if (info.age >= 0) add("${info.age}+")
    val minutes = info.durationSec / 60
    if (minutes > 0) add("${minutes} мин")
    if (info.quality.isNotBlank()) add(info.quality)
    addAll(
        info.genres.asSequence()
            .flatMap { it.split(",").asSequence() }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(2)
            .toList()
    )
}.joinToString(" · ")

@Preview(showBackground = true)
@Composable
private fun DetailsScreenPreview() {
    val movie = Movie(
        dbId = 42,
        title = "Рыцарь Семи Королевств",
        type = MovieType.SERIAL,
        img = "",
        info = MovieInfo(
            year = 2026,
            age = 16,
            durationSec = 3300,
            genres = listOf("Фэнтези, драма"),
            actors = listOf("Питер Клэффи, Декстер Сол Анселл"),
            description = "Вестерос за 100 лет до борьбы Дейенерис Таргариен за Железный Трон.",
        ),
        seasons = emptyList(),
    )
    TvTheme {
        DetailsScreen(
            movie = movie,
            isFavorite = false,
            resumeTarget = null,
            progress = emptyMap(),
            selectedSeasonIndex = 0,
            cta = CtaUi(labelRes = R.string.play),
            isUpdating = false,
            updatePercent = -1,
            onPlay = {},
            onPlayEpisode = {},
            onPlaySource = {},
            onToggleFavorite = {},
            onUpdate = {},
            onCancelUpdate = {},
            onSelectSeason = {},
            onSearch = { _, _ -> },
        )
    }
}
