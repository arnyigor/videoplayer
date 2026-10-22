package com.arny.mobilecinema.presentation.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arny.mobilecinema.presentation.tv.ui.theme.TvDimens

data class SeasonTab(
    val id: Int,
    val label: String,
)

/**
 * Компактный селектор сезона: для 1–4 сезонов — горизонтальные вкладки (ТЗ §6.3).
 * Выбор применяется по OK; фокус и выбранное состояние — разные состояния чипа.
 */
@Composable
fun SeasonTabs(
    seasons: List<SeasonTab>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = TvDimens.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = seasons, key = { it.id }) { season ->
            TvFilterChip(
                label = season.label,
                selected = season.id == selectedId,
                onClick = { onSelect(season.id) },
            )
        }
    }
}
