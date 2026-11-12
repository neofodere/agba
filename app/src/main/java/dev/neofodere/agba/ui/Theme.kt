package dev.neofodere.agba.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import dev.neofodere.agba.app
import dev.neofodere.agba.core.Platform
import dev.neofodere.agba.data.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD17F),
    onPrimary = Color(0xFF00390F),
    primaryContainer = Color(0xFF1F5A2A),
    onPrimaryContainer = Color(0xFFA7F3A4),
    secondary = Color(0xFFB7CCB4),
    onSecondary = Color(0xFF233425),
    secondaryContainer = Color(0xFF394B3A),
    onSecondaryContainer = Color(0xFFD3E8D0),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE1E3DD),
    surface = Color(0xFF1B1B1B),
    onSurface = Color(0xFFE1E3DD),
    surfaceVariant = Color(0xFF2A2D2A),
    onSurfaceVariant = Color(0xFFC0C9BE),
    outline = Color(0xFF8A9389),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7F2B2),
    onPrimaryContainer = Color(0xFF002106),
    secondary = Color(0xFF52634F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD4E8D0),
    onSecondaryContainer = Color(0xFF101F10),
    background = Color(0xFFF2F4F1),
    onBackground = Color(0xFF181D18),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF181D18),
    surfaceVariant = Color(0xFFDDE5DA),
    onSurfaceVariant = Color(0xFF414941),
    outline = Color(0xFF717970),
)

@Composable
fun AgbaTheme(content: @Composable () -> Unit) {
    val mode = LocalContext.current.app.themeMode.value
    val dark = when (mode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            content = content,
        )
    }
}

@Composable
fun containerColor(): Color = if (LocalDarkTheme.current) Color(0xFF243024) else Color(0xFFDCEEDA)

val LocalDarkTheme = staticCompositionLocalOf { false }

const val COVER_TOP_ARGB = 0xFFC8E6C9.toInt()
const val COVER_BOTTOM_ARGB = 0xFF8FCB93.toInt()
val CoverGradientTop = Color(COVER_TOP_ARGB)
val CoverGradientBottom = Color(COVER_BOTTOM_ARGB)

@Composable
fun ConsoleCover(platform: Platform, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(CoverGradientTop, CoverGradientBottom))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(platform.iconRes),
            contentDescription = platform.label,
            modifier = Modifier.fillMaxSize(0.62f),
            contentScale = ContentScale.Fit,
        )
    }
}
