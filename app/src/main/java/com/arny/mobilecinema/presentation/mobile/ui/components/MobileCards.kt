package com.arny.mobilecinema.presentation.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.arny.mobilecinema.R
import com.arny.mobilecinema.presentation.mobile.ui.theme.MobileColors
import com.arny.mobilecinema.presentation.mobile.ui.theme.MobileDimens
import com.arny.mobilecinema.presentation.mobile.ui.theme.MobileTheme
import androidx.compose.ui.res.stringResource

/**
 * Карточки мобильного UI (ТЗ §20.2, план MOB-13).
 *
 * Постер 2:3 — каталог и подборки; 16:9 — «Продолжить» и эпизоды.
 * Тап-зона карточки не меньше 48 dp, название — не больше двух строк.
 * Ширину задаёт вызывающая сторона: в ряду — фиксированная, в сетке — по ячейке.
 */
@Composable
fun MobilePosterCard(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(MobileDimens.PosterWidth),
    subtitle: String? = null,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(MobileDimens.CornerRadius),
        // Цвет из токенов: тема может быть светлой, а карточка обязана остаться тёмной.
        colors = CardDefaults.cardColors(containerColor = MobileColors.Surface),
    ) {
        Column {
            AsyncImage(
                model = posterUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(MobileColors.SurfaceElevated),
            )
            // Резерв под две строки названия: футер карточки всегда одной высоты,
            // иначе соседние ячейки сетки разной высоты (аудит §3.6, UI-06).
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = title,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MobileColors.OnBackground,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        color = MobileColors.TextSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** Карточка «Продолжить просмотр»: 16:9, полоса прогресса, серия и остаток времени. */
@Composable
fun MobileContinueCard(
    title: String,
    imageUrl: String?,
    episodeLabel: String?,
    progressFraction: Float?,
    remainingLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(MobileDimens.ContinueCardWidth),
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(MobileDimens.CornerRadius),
        colors = CardDefaults.cardColors(containerColor = MobileColors.Surface),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(MobileDimens.ContinueCardAspect),
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MobileColors.SurfaceElevated),
                )
                if (progressFraction != null) {
                    val fraction = progressFraction.coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color(0x66000000)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(3.dp)
                                .background(MobileColors.Accent),
                        )
                    }
                }
            }
            Column(
                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MobileColors.OnBackground,
                )
                val meta = listOfNotNull(
                    episodeLabel?.takeIf { it.isNotBlank() },
                    remainingLabel?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MobileColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
fun MobileSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onActionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MobileDimens.ScreenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MobileColors.OnBackground,
        )
        if (onActionLabel != null && onAction != null) {
            TextButton(onClick = onAction, modifier = Modifier.heightIn(min = MobileDimens.MinTouchTarget)) {
                Text(
                    text = onActionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MobileColors.Accent,
                )
            }
        }
    }
}

@Composable
fun MobileErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MobileDimens.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MobileDimens.RowGap),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MobileColors.OnBackground,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.height(MobileDimens.MinTouchTarget),
        ) {
            Text(text = stringResource(R.string.retry))
        }
    }
}

@Composable
fun MobileEmptyState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(MobileDimens.ScreenPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MobileColors.TextSecondary,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0F13)
@Composable
private fun MobileCardsPreview() {
    MobileTheme(darkTheme = true) {
        Column(
            Modifier
                .background(MobileColors.Background)
                .padding(MobileDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(MobileDimens.CardGap),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(MobileDimens.CardGap)) {
                MobilePosterCard(
                    title = "Рыцарь Семи Королевств",
                    posterUrl = null,
                    subtitle = "2026 · сериал",
                    onClick = {},
                )
                MobilePosterCard(
                    title = "Проект «Конец света»",
                    posterUrl = null,
                    subtitle = "2026",
                    onClick = {},
                )
            }
            MobileContinueCard(
                title = "Колония",
                imageUrl = null,
                episodeLabel = "S02E05",
                progressFraction = 0.42f,
                remainingLabel = "осталось 42 мин",
                onClick = {},
            )
            MobileSectionHeader(title = "Продолжить просмотр")
        }
    }
}
