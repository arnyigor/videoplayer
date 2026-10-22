package com.arny.mobilecinema.presentation.mobile.ui.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.utils.getWithDomain
import com.arny.mobilecinema.domain.models.HomeHighlights
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.presentation.mobile.home.ContinueCardModel
import com.arny.mobilecinema.presentation.mobile.home.MobileHomeBlock
import com.arny.mobilecinema.presentation.mobile.home.MobileHomeBlockType
import com.arny.mobilecinema.presentation.mobile.home.MobileHomeMode
import com.arny.mobilecinema.presentation.mobile.home.MobileHomeUiState
import com.arny.mobilecinema.presentation.mobile.ui.components.MobileContinueCard
import com.arny.mobilecinema.presentation.mobile.ui.components.MobileEmptyState
import com.arny.mobilecinema.presentation.mobile.ui.components.MobileErrorState
import com.arny.mobilecinema.presentation.mobile.ui.components.MobilePosterCard
import com.arny.mobilecinema.presentation.mobile.ui.components.MobileSectionHeader
import com.arny.mobilecinema.presentation.mobile.ui.theme.MobileColors
import com.arny.mobilecinema.presentation.mobile.ui.theme.MobileDimens
import com.arny.mobilecinema.presentation.mobile.ui.theme.MobileTheme

/**
 * Главная на Compose (ТЗ ч. II §20.1, план MOB-09C).
 *
 * Порядок блоков считает `MobileHomeRowsFactory`, метки карточек — `ContinueCardMapper`,
 * поэтому экран ничего не решает сам: он показывает то, что пришло в состоянии.
 * Все действия — наружу через колбэки, чтобы экран оставался тестируемым в Preview.
 */
@Composable
fun MobileHomeScreen(
    state: MobileHomeUiState,
    onPlay: (Long) -> Unit,
    onOpenDetails: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(MobileColors.Background),
    ) {
        when (state.mode) {
            MobileHomeMode.LOADING -> MobileHomeSkeleton()

            MobileHomeMode.ERROR -> MobileErrorState(
                message = stringResource(R.string.mobile_home_error),
                onRetry = onRetry,
            )

            MobileHomeMode.EMPTY -> MobileEmptyState(message = stringResource(R.string.tv_catalog_empty))

            MobileHomeMode.CONTENT -> {
                val content = state.content
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = MobileDimens.SectionGap,
                        bottom = MobileDimens.SectionGap,
                    ),
                ) {
                    items(content.blocks, key = { it.id }) { block ->
                        HomeBlock(
                            block = block,
                            baseUrl = state.baseUrl,
                            onPlay = onPlay,
                            onOpenDetails = onOpenDetails,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeBlock(
    block: MobileHomeBlock,
    baseUrl: String,
    onPlay: (Long) -> Unit,
    onOpenDetails: (Long) -> Unit,
) {
    Column(Modifier.padding(bottom = MobileDimens.SectionGap)) {
        MobileSectionHeader(title = stringResource(block.type.titleRes))
        if (block.type == MobileHomeBlockType.CONTINUE) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = MobileDimens.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(MobileDimens.CardGap),
            ) {
                items(block.continueCards, key = { it.movieDbId }) { card ->
                    ContinueCard(card = card, baseUrl = baseUrl, onPlay = onPlay)
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = MobileDimens.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(MobileDimens.CardGap),
            ) {
                items(block.movies, key = { it.dbId }) { movie ->
                    MobilePosterCard(
                        title = movie.title,
                        posterUrl = movie.posterUrl(baseUrl),
                        subtitle = movie.year.takeIf { it > 0 }?.toString(),
                        onClick = { onOpenDetails(movie.dbId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(card: ContinueCardModel, baseUrl: String, onPlay: (Long) -> Unit) {
    MobileContinueCard(
        title = card.title,
        imageUrl = card.posterUrl.takeIf { it.isNotBlank() }?.getWithDomain(baseUrl),
        episodeLabel = card.episodeLabel,
        progressFraction = card.progressFraction,
        remainingLabel = card.remaining?.let { stringResource(it.resId, it.minutes) },
        onClick = { onPlay(card.movieDbId) },
    )
}

/** Скелетон фиксированной геометрии: сетка не «прыгает», когда приезжают данные. */
@Composable
private fun MobileHomeSkeleton() {
    Column(Modifier.fillMaxSize()) {
        repeat(3) {
            Box(
                Modifier
                    .padding(
                        start = MobileDimens.ScreenPadding,
                        end = MobileDimens.ScreenPadding,
                        top = MobileDimens.SectionGap,
                    )
                    .fillMaxWidth(0.4f)
                    .height(20.dp)
                    .background(MobileColors.Surface),
            )
            Row(
                Modifier.padding(
                    start = MobileDimens.ScreenPadding,
                    top = MobileDimens.RowGap,
                ),
                horizontalArrangement = Arrangement.spacedBy(MobileDimens.CardGap),
            ) {
                repeat(3) {
                    Box(
                        Modifier
                            .height(MobileDimens.PosterHeight)
                            .background(MobileColors.Surface)
                            .fillMaxWidth(0.28f),
                    )
                }
            }
        }
    }
}

private fun ViewMovie.posterUrl(baseUrl: String): String =
    if (img.isBlank()) "" else img.getWithDomain(baseUrl)

@Preview(showBackground = true, backgroundColor = 0xFF0D0F13)
@Composable
private fun MobileHomePreview() {
    MobileTheme(darkTheme = true) {
        MobileHomeScreen(
            state = MobileHomeUiState(
                isLoading = false,
                highlights = HomeHighlights(
                    recent = listOf(ViewMovie(dbId = 1, title = "Проект «Конец света»", year = 2026, ratingImdb = 8.4)),
                    forYou = listOf(ViewMovie(dbId = 2, title = "Колония", year = 2026)),
                ),
                continueCards = listOf(
                    ContinueCardModel(
                        movieDbId = 3,
                        title = "Кэрри",
                        posterUrl = "",
                        episodeLabel = "S01E01",
                        progressFraction = 0.25f,
                        remaining = null,
                    )
                ),
            ),
            onPlay = {},
            onOpenDetails = {},
            onRetry = {},
        )
    }
}
