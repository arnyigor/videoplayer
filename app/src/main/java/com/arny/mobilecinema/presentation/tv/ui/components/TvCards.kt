package com.arny.mobilecinema.presentation.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.arny.mobilecinema.presentation.tv.ui.theme.TvColors
import com.arny.mobilecinema.presentation.tv.ui.theme.TvDimens
import com.arny.mobilecinema.presentation.tv.ui.theme.TvTheme

/**
 * Постер тайтла 2:3 (ТЗ §4.3). Название — не более двух строк, без описания.
 */
@Composable
fun PosterCard(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .width(TvDimens.PosterWidth)
            .height(TvDimens.PosterHeight),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = posterUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(TvColors.Surface),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xE6000000)),
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = TvColors.OnBackground,
                )
            }
        }
    }
}

/**
 * Карточка «Продолжить просмотр» / эпизода 16:9 с полосой прогресса.
 */
@Composable
fun ContinueWatchingCard(
    title: String,
    episodeLabel: String?,
    imageUrl: String?,
    progressFraction: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.width(TvDimens.EpisodeWidth),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(TvDimens.EpisodeHeight)
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TvColors.Surface),
                )
                if (progressFraction != null) {
                    val fraction = progressFraction.coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color(0x66000000))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(4.dp)
                                .background(TvColors.Accent)
                        )
                    }
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    // Плашка под постером: без явного фона карточка показывает светлый surface темы.
                    .background(TvColors.Surface)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (!episodeLabel.isNullOrBlank()) {
                    Text(
                        text = episodeLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = TvColors.Accent,
                    )
                }
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = TvColors.OnBackground,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PosterCardPreview() {
    TvTheme {
        Box(Modifier.background(TvColors.Background).padding(24.dp)) {
            PosterCard(
                title = "Проект «Конец света»",
                posterUrl = null,
                onClick = {},
            )
        }
    }
}
