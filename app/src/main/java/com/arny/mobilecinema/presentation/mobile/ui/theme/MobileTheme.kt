package com.arny.mobilecinema.presentation.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Токены мобильного Compose-UI (ТЗ ч. II §20–§23, план MOB-13).
 *
 * Палитра единая с TV-частью: тёмный фон и тот же брендовый акцент, чтобы приложение
 * не выглядело как два разных продукта. Геометрия — мобильная: паддинг 16 dp,
 * зазор 8–12 dp, секция 20–28 dp, минимальная тач-зона 48 dp.
 */
object MobileColors {
    val Background = Color(0xFF0D0F13)
    val Surface = Color(0xFF1C2028)
    val SurfaceElevated = Color(0xFF252A34)
    val OnBackground = Color(0xFFF4F6FA)
    val TextSecondary = Color(0xFFABA9B9)

    /** Единственный брендовый акцент: активные вкладки, прогресс, выбранные chips. */
    val Accent = Color(0xFFB6A0FF)

    /** Текст на акценте: акцент светлый, поэтому текст тёмный. */
    val OnAccent = Color(0xFF101018)

    /** Границы невыбранных фильтров: читаемее, чем «выключенный» вид (аудит UI-03). */
    val Outline = Color(0xFF666978)
    val Error = Color(0xFFFF7180)
    val NavigationBar = Color(0xFF12151B)
}

object MobileDimens {
    val ScreenPadding = 16.dp
    val CardGap = 12.dp
    val RowGap = 8.dp
    val SectionGap = 24.dp

    val PosterWidth = 158.dp
    val PosterHeight = 237.dp
    val LandscapeWidth = 220.dp
    val LandscapeHeight = 124.dp

    /**
     * Карточка «Продолжить»: в базе лежат постеры 2:3, поэтому кадр показываем в их пропорциях,
     * иначе от постера остаётся середина без названия. Ширина меньше постерной, чтобы ряд
     * оставался компактным.
     */
    val ContinueCardWidth = 132.dp
    val ContinueCardAspect = 2f / 3f
    val EpisodeThumbWidth = 128.dp
    val EpisodeThumbHeight = 72.dp

    /** Минимальная сенсорная зона по ТЗ §20.2. */
    val MinTouchTarget = 48.dp
    val CornerRadius = 12.dp

    /** Радиус chips: капсула, но не «таблетка» на всю высоту строки. */
    val ChipRadius = 16.dp

    /** Ширина миниатюры в результатах поиска (пропорция постера 2:3). */
    val SearchThumbWidth = 72.dp
    val SearchThumbHeight = 108.dp
}

private val MobileDarkScheme = darkColorScheme(
    primary = MobileColors.Accent,
    onPrimary = MobileColors.OnAccent,
    background = MobileColors.Background,
    onBackground = MobileColors.OnBackground,
    surface = MobileColors.Surface,
    onSurface = MobileColors.OnBackground,
    surfaceVariant = MobileColors.SurfaceElevated,
    onSurfaceVariant = MobileColors.TextSecondary,
    outline = MobileColors.Outline,
    error = MobileColors.Error,
)

private val MobileLightScheme = lightColorScheme(
    primary = MobileColors.Accent,
    background = Color(0xFFF7F7FA),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun MobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) MobileDarkScheme else MobileLightScheme,
        typography = Typography(),
        content = content,
    )
}
