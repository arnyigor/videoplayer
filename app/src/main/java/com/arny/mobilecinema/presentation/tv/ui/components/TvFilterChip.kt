@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.arny.mobilecinema.presentation.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.FilterChip
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

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
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
