package com.novelarrow.reader.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.novelarrow.reader.data.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    secondary = AccentPurple,
    tertiary = AccentPink,
    background = BgDark0,
    surface = BgDark1,
    surfaceVariant = BgDark2,
    onPrimary = TextDark,
    onBackground = TextDark,
    onSurface = TextDark
)

private val SepiaColorScheme = lightColorScheme(
    primary = SepiaAccent,
    secondary = AccentPurple,
    tertiary = AccentBlue,
    background = SepiaBg,
    surface = SepiaSurface,
    surfaceVariant = SepiaSurface,
    onPrimary = SepiaBg,
    onBackground = SepiaText,
    onSurface = SepiaText
)

private val LightColorScheme = lightColorScheme(
    primary = AccentBlue,
    secondary = AccentPurple,
    tertiary = AccentPink,
    background = LightBg,
    surface = LightSurface,
    surfaceVariant = Color(0xFFF1F5F9),
    onPrimary = LightSurface,
    onBackground = LightText,
    onSurface = LightText
)

@Composable
fun NovelArrowReaderTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        ThemeMode.DARK -> DarkColorScheme
        ThemeMode.SEPIA -> SepiaColorScheme
        ThemeMode.LIGHT -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
