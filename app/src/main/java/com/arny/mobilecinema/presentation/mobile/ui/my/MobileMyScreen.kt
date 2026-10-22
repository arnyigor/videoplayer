package com.arny.mobilecinema.presentation.mobile.ui.my

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arny.mobilecinema.R
import com.arny.mobilecinema.presentation.mobile.ui.theme.MobileColors
import com.arny.mobilecinema.presentation.mobile.ui.theme.MobileDimens
import com.arny.mobilecinema.presentation.mobile.ui.theme.MobileTheme

/**
 * Вкладка «Моё» (MOB-08A): входы в историю, избранное и настройки.
 *
 * Отдельного профиля пока нет, поэтому вкладка — список разделов, а не шестая колонка меню.
 * Экран без состояния: всё, что он делает, — сообщает о выбранном пункте.
 */
@Composable
fun MobileMyScreen(
    onOpenHistory: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MobileColors.Background)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.menu_my),
            style = MaterialTheme.typography.titleLarge,
            color = MobileColors.OnBackground,
            modifier = Modifier.padding(
                start = MobileDimens.ScreenPadding,
                end = MobileDimens.ScreenPadding,
                top = MobileDimens.ScreenPadding,
                bottom = MobileDimens.RowGap,
            ),
        )
        MyRow(
            iconRes = R.drawable.ic_history,
            title = stringResource(R.string.menu_history),
            onClick = onOpenHistory,
        )
        MyRow(
            iconRes = R.drawable.outline_bookmark_24,
            title = stringResource(R.string.menu_favorite),
            onClick = onOpenFavorites,
        )
        // Настройки — отдельная группа внизу смысловых разделов, а не третий равноправный
        // пункт библиотеки (аудит §6.4). Разделяет группы только отступ: лишняя линия рядом
        // с дивидером строки читалась как двойной разделитель.
        Spacer(Modifier.height(MobileDimens.SectionGap))
        MyRow(
            iconRes = R.drawable.ic_settings,
            title = stringResource(R.string.menu_preferences),
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun MyRow(
    @DrawableRes iconRes: Int,
    title: String,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MobileDimens.MinTouchTarget)
                .clickable(onClick = onClick)
                .padding(horizontal = MobileDimens.ScreenPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MobileColors.TextSecondary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MobileColors.OnBackground,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = MobileColors.SurfaceElevated)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0F13)
@Composable
private fun MobileMyPreview() {
    MobileTheme(darkTheme = true) {
        MobileMyScreen(onOpenHistory = {}, onOpenFavorites = {}, onOpenSettings = {})
    }
}
