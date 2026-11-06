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

// Fallback palette: current Destiny 2 UI identity mapped onto the
// Material 3 role system (used when dynamic color is off or unavailable).
//   Exotic     #CDAD36  → primary
//   Legendary  #4F3663  → secondary
//   Rare       #567E9D  → tertiary
//   Common     #C2BBB2  → warm surface text
//   D2 charcoal #171515 → background/surface
private val DarkColors = darkColorScheme(
    primary = Color(0xFFCDAD36),
    onPrimary = Color(0xFF241A05),
    primaryContainer = Color(0xFF4A3D10),
    onPrimaryContainer = Color(0xFFF0DFA0),
    secondary = Color(0xFFB7A0D6),
    onSecondary = Color(0xFF2B1F3E),
    secondaryContainer = Color(0xFF4A3562),
    onSecondaryContainer = Color(0xFFE8D6F7),
    tertiary = Color(0xFF7FA8CC),
    onTertiary = Color(0xFF182431),
    tertiaryContainer = Color(0xFF31465B),
    onTertiaryContainer = Color(0xFFBED6EA),
    background = Color(0xFF121010),
    onBackground = Color(0xFFE3DFD8),
    surface = Color(0xFF171515),
    onSurface = Color(0xFFE3DFD8),
    surfaceVariant = Color(0xFF232020),
    onSurfaceVariant = Color(0xFFA39E97),
    surfaceContainerLowest = Color(0xFF0D0B0B),
    surfaceContainerLow = Color(0xFF141212),
    surfaceContainer = Color(0xFF191716),
    surfaceContainerHigh = Color(0xFF201E1D),
    surfaceContainerHighest = Color(0xFF272524),
    outline = Color(0xFF423F3D),
    outlineVariant = Color(0xFF2C2A29),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6E5510),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF0E28A),
    onPrimaryContainer = Color(0xFF241A02),
    secondary = Color(0xFF4F3663),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEBDFF2),
    onSecondaryContainer = Color(0xFF241433),
    tertiary = Color(0xFF4A6E8F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE1EAF1),
    onTertiaryContainer = Color(0xFF16273A),
    background = Color(0xFFFBF9F3),
    onBackground = Color(0xFF1E1B16),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFECE9E0),
    onSurfaceVariant = Color(0xFF4B4842),
    surfaceContainerLow = Color(0xFFF5F3ED),
    surfaceContainer = Color(0xFFEFEEE8),
    surfaceContainerHigh = Color(0xFFE9E8E2),
    surfaceContainerHighest = Color(0xFFE3E2DC),
    outline = Color(0xFF8F8C84),
)

// ---------- Reading themes (applied to article content only) ----------

// Sepia: warm cream background, soft brown text — comfortable for long reading
private val SepiaColors = lightColorScheme(
    background = Color(0xFFF5F0E6),
    onBackground = Color(0xFF4A3F35),
    surface = Color(0xFFF5F0E6),
    onSurface = Color(0xFF4A3F35),
    surfaceVariant = Color(0xFFE8E0D2),
    onSurfaceVariant = Color(0xFF6B6055),
    outline = Color(0xFFB9AE9E),
    primary = Color(0xFF8A6D3B),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF7A5A2B),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF6E5B4E),
    onTertiary = Color(0xFFFFFFFF),
)

// Amber: Destiny-inspired gold-on-dark for reading in low light
private val AmberColors = darkColorScheme(
    background = Color(0xFF1A140A),
    onBackground = Color(0xFFEAD8A8),
    surface = Color(0xFF1A140A),
    onSurface = Color(0xFFEAD8A8),
    surfaceVariant = Color(0xFF2A2010),
    onSurfaceVariant = Color(0xFFC9B18A),
    outline = Color(0xFF4A3A20),
    primary = Color(0xFFCDAD36),
    onPrimary = Color(0xFF241A05),
    secondary = Color(0xFF4F3663),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF7FA8CC),
    onTertiary = Color(0xFF182431),
)

@Composable
fun GrimoireTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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

/// Wraps article content with a reading theme overlay (sepia / amber).
/// Falls back to the current MaterialTheme colors for DEFAULT.
@Composable
fun ReadingThemeOverlay(
    theme: com.grimoire.android.data.ReadingTheme,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (theme) {
        com.grimoire.android.data.ReadingTheme.SEPIA -> SepiaColors
        com.grimoire.android.data.ReadingTheme.AMBER -> AmberColors
        com.grimoire.android.data.ReadingTheme.DEFAULT -> MaterialTheme.colorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

/// Returns a TextStyle with the selected reading font family applied.
fun ReadingFontStyle(
    family: com.grimoire.android.data.FontFamily,
    base: androidx.compose.ui.text.TextStyle,
): androidx.compose.ui.text.TextStyle {
    val typeface = when (family) {
        com.grimoire.android.data.FontFamily.SERIF -> androidx.compose.ui.text.font.FontFamily.Serif
        com.grimoire.android.data.FontFamily.MONOSPACE -> androidx.compose.ui.text.font.FontFamily.Monospace
        com.grimoire.android.data.FontFamily.SANS_SERIF -> androidx.compose.ui.text.font.FontFamily.Default
    }
    return base.copy(fontFamily = typeface)
}