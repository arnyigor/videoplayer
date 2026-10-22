package com.arny.mobilecinema.presentation.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.arny.mobilecinema.presentation.tv.ui.theme.TvColors
import com.arny.mobilecinema.presentation.tv.ui.theme.TvDimens
import com.arny.mobilecinema.presentation.tv.ui.theme.TvTheme

/**
 * Hero-блок главной (ТЗ §3.2): backdrop, метаданные, два действия, без autoplay.
 */
@Composable
fun TvHero(
    title: String,
    meta: String,
    description: String,
    imageUrl: String?,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp)
    ) {
        AsyncImage(
            model = imageUrl,
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
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xF20D0F13), Color(0x000D0F13)),
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xE60D0F13)),
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .widthIn(max = 760.dp)
                .padding(
                    start = TvDimens.ScreenHorizontal,
                    end = TvDimens.ScreenHorizontal,
                    bottom = TvDimens.ScreenVertical + 12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.displaySmall,
                color = TvColors.OnBackground,
            )
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvColors.TextSecondary,
                )
            }
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = TvColors.OnBackground,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Button(onClick = onPrimary) {
                    Text(text = primaryLabel)
                }
                OutlinedButton(onClick = onSecondary) {
                    Text(text = secondaryLabel)
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    Row(
        modifier = modifier.padding(
            horizontal = TvDimens.ScreenHorizontal,
            vertical = 12.dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TvColors.OnBackground,
        )
        if (count != null && count > 0) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = TvColors.TextSecondary,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TvHeroPreview() {
    TvTheme {
        TvHero(
            title = "Проект «Конец света»",
            meta = "2026 · 16+ · 2 ч 08 мин · Фантастика",
            description = "Краткое описание фильма в две-три строки, чтобы проверить перенос и читаемость.",
            imageUrl = null,
            primaryLabel = "Смотреть",
            onPrimary = {},
            secondaryLabel = "Подробнее",
            onSecondary = {},
        )
    }
}
