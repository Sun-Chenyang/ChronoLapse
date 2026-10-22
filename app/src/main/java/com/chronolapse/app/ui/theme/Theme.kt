package com.chronolapse.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// camera UI palette: always dark, amber accent
val Amber = Color(0xFFFFB63B)
val OnAmber = Color(0xFF1F1405)
val RecRed = Color(0xFFFF4D5E)
val InkBg = Color(0xFF0A0A0C)
val InkSurface = Color(0xFF151519)
val InkCard = Color(0xFF1A1A1F)
val InkGlass = Color(0x730C0C10)
val TextPrimary = Color(0xFFF5F5F7)
val TextDim = Color(0x99F5F5F7)
val FaintStroke = Color(0x29FFFFFF)
val GlassFill = Color(0x14FFFFFF)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = OnAmber,
    primaryContainer = Color(0x33FFB63B),
    onPrimaryContainer = Amber,
    secondary = TextDim,
    background = InkBg,
    onBackground = TextPrimary,
    surface = InkSurface,
    onSurface = TextPrimary,
    surfaceVariant = InkCard,
    onSurfaceVariant = TextDim,
    outline = FaintStroke,
    error = RecRed,
)

/** The app is always dark - it is a camera. */
@Composable
fun ChronoTheme(content: @Composable () -> Unit) {
    // isSystemInDarkTheme intentionally ignored: single fixed theme
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
