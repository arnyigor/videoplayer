@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.arny.mobilecinema.presentation.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.MaterialTheme
import com.arny.mobilecinema.presentation.tv.ui.theme.TvColors
import androidx.tv.material3.Text
import androidx.compose.ui.text.style.TextOverflow

/**
 * Чип фильтра/сортировки. `selected` — применение фильтра, фокус — отдельное состояние.
 */
@Composable
fun TvFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        // Как на мобиле: выбранный чип — тёмная подложка и акцентный текст,
        // в фокусе — акцентная заливка с тёмным текстом.
        colors = FilterChipDefaults.colors(
            containerColor = TvColors.Surface,
            contentColor = TvColors.OnBackground,
            focusedContainerColor = TvColors.Accent,
            focusedContentColor = Color(0xFF24143D),
            selectedContainerColor = Color(0xFF33304A),
            selectedContentColor = TvColors.Accent,
            focusedSelectedContainerColor = TvColors.Accent,
            focusedSelectedContentColor = Color(0xFF24143D),
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            // Одна строка: перенос раздувал ряд чипов и уводил контент под ним вниз.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
