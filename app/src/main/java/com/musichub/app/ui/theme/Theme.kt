package com.musichub.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import com.musichub.app.domain.model.ThemeMode

enum class UiStyle { Glass, Matte }

/** Current visual style, readable from any component. */
val LocalUiStyle = compositionLocalOf { UiStyle.Glass }

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B6CF0),
    secondary = Color(0xFF8E3BE0),
    background = Color(0xFFF4F5F7),
    surface = Color.White,
    surfaceVariant = Color(0xFFE6E8EC),
    onBackground = Color(0xFF111318),
    onSurface = Color(0xFF111318),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    secondary = Color(0xFFC58BFF),
    background = Color(0xFF0B0B18),
    surface = Color(0xFF16162A),
    surfaceVariant = Color(0xFF22223A),
)

private val OledColors = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    secondary = Color(0xFFC58BFF),
    background = Color.Black, // true black #000000
    surface = Color(0xFF121214),
    surfaceVariant = Color(0xFF1E1E22),
    onBackground = Color.White,
    onSurface = Color.White,
    outline = Color(0xFF3A3A40),
)

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.OLED -> true
}

@Composable
fun MusicHubTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val scheme = when (themeMode) {
        ThemeMode.SYSTEM -> if (isSystemInDarkTheme()) DarkColors else LightColors
        ThemeMode.LIGHT -> LightColors
        ThemeMode.DARK -> DarkColors
        ThemeMode.OLED -> OledColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Screen background: solid black for OLED, soft gradients for Dark/Light. */
@Composable
fun appBackgroundBrush(): Brush {
    val bg = MaterialTheme.colorScheme.background
    return when {
        bg == Color.Black -> SolidColor(Color.Black)
        bg.luminance() < 0.5f -> Brush.linearGradient(
            listOf(Color(0xFF1B1038), Color(0xFF0B0B18), Color(0xFF0A2A44))
        )
        else -> Brush.linearGradient(
            listOf(Color(0xFFF8F9FB), Color.White, Color(0xFFEDEFF3))
        )
    }
}
