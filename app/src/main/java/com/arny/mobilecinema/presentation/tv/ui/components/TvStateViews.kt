package com.arny.mobilecinema.presentation.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.arny.mobilecinema.R
import com.arny.mobilecinema.presentation.tv.ui.theme.TvColors
import com.arny.mobilecinema.presentation.tv.ui.theme.TvTheme

/**
 * Единые состояния экрана (ТЗ §11.2). Скелетоны и заглушки не должны быть фокусируемыми.
 */
@Composable
fun TvLoadingState(
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message ?: stringResource(R.string.loading),
            style = MaterialTheme.typography.bodyLarge,
            color = TvColors.TextSecondary,
        )
    }
}

@Composable
fun TvEmptyState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = TvColors.TextSecondary,
        )
    }
}

@Composable
fun TvErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = TvColors.OnBackground,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text(text = stringResource(R.string.retry))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TvErrorStatePreview() {
    TvTheme {
        TvErrorState(
            message = "Не удалось загрузить каталог",
            onRetry = {},
        )
    }
}
