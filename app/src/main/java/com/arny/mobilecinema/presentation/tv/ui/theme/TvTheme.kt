package com.arny.mobilecinema.presentation.tv.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

/**
 * Токены TV-интерфейса (ТЗ §10). Пока используются Compose-экранами; Leanback-экраны
 * берут значения из `res/values/dimens.xml` и `colors.xml`.
 */
object TvColors {
    val Background = Color(0xFF0D0F13)
    val Surface = Color(0xFF1C2028)
    val OnBackground = Color(0xFFF4F6FA)
    val Accent = Color(0xFFB6A0FF)
    val TextSecondary = Color(0xFFABA9B9)
    val FocusStroke = Color(0xFFFFFFFF)
}

object TvDimens {
    val ScreenHorizontal = 48.dp
    val ScreenVertical = 27.dp
    val PosterWidth = 124.dp
    val PosterHeight = 186.dp
    val EpisodeWidth = 196.dp
    val EpisodeHeight = 110.dp
    val CardGap = 16.dp
    val SectionGap = 28.dp
    val FocusStroke = 3.dp
}

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        typography = Typography(),
        content = content,
    )
}


@Preview(showBackground = true)
@Composable
private fun TvThemePreview() {
    TvTheme {
        Box(Modifier.fillMaxSize().background(TvColors.Background)) {
            BasicText(
                text = "Anwap Movies",
                style = TextStyle(color = TvColors.OnBackground, fontSize = 22.sp),
            )
        }
    }
}
