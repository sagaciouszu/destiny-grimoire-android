package com.grimoire.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback palette: deep aubergine + gold, grimoire vibes.
// (Used only when dynamic color is disabled or the device is pre-S.)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFD8B25A),
    onPrimary = Color(0xFF241626),
    primaryContainer = Color(0xFF4A3520),
    onPrimaryContainer = Color(0xFFFFDF9E),
    secondary = Color(0xFFB9A9C4),
    onSecondary = Color(0xFF2E2337),
    secondaryContainer = Color(0xFF3D3049),
    onSecondaryContainer = Color(0xFFE2D5EA),
    tertiary = Color(0xFFE7D9BE),
    onTertiary = Color(0xFF2B241A),
    background = Color(0xFF1B131F),
    onBackground = Color(0xFFEDE4DA),
    surface = Color(0xFF251A2E),
    onSurface = Color(0xFFEDE4DA),
    surfaceVariant = Color(0xFF3A2B45),
    onSurfaceVariant = Color(0xFFD6C7D8),
    surfaceContainerLowest = Color(0xFF161016),
    surfaceContainerLow = Color(0xFF221A28),
    surfaceContainer = Color(0xFF271F2E),
    surfaceContainerHigh = Color(0xFF322A39),
    surfaceContainerHighest = Color(0xFF3D3545),
    outline = Color(0xFF5E5366),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF7A5200),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDF9E),
    onPrimaryContainer = Color(0xFF271900),
    secondary = Color(0xFF6E5E74),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEBD9EF),
    onSecondaryContainer = Color(0xFF231A2B),
    tertiary = Color(0xFF5C5142),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFBF5),
    onBackground = Color(0xFF1E1A17),
    surface = Color(0xFFFFFBF5),
    onSurface = Color(0xFF1E1A17),
    surfaceVariant = Color(0xFFEDE0D3),
    onSurfaceVariant = Color(0xFF4E453C),
    surfaceContainer = Color(0xFFF5EDE3),
    surfaceContainerHigh = Color(0xFFEFE6DC),
    surfaceContainerHighest = Color(0xFFE9DFD5),
    outline = Color(0xFF9B8C80),
)

@Composable
fun GrimoireTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
