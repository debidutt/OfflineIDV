@file:Suppress("FunctionName") // Jetpack Compose public functions follow Android's PascalCase convention.

package com.ing.offlineidv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AtlasBlue = Color(0xFF165DFF)
private val AtlasNavy = Color(0xFF10233F)
private val AtlasMint = Color(0xFF3AC8A6)
private val AtlasIce = Color(0xFFF4F7FC)

private val LightColors =
    lightColorScheme(
        primary = AtlasBlue,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDCE6FF),
        onPrimaryContainer = AtlasNavy,
        secondary = Color(0xFF006B57),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFB7F3DF),
        onSecondaryContainer = Color(0xFF002019),
        tertiary = AtlasMint,
        background = AtlasIce,
        onBackground = AtlasNavy,
        surface = Color.White,
        onSurface = AtlasNavy,
        surfaceVariant = Color(0xFFE7ECF4),
        onSurfaceVariant = Color(0xFF445064),
        error = Color(0xFFBA1A1A),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFB4C5FF),
        onPrimary = Color(0xFF002A78),
        primaryContainer = Color(0xFF0040A8),
        onPrimaryContainer = Color(0xFFDCE6FF),
        secondary = Color(0xFF91D7C2),
        onSecondary = Color(0xFF00382D),
        background = Color(0xFF0C1524),
        onBackground = Color(0xFFE2E8F4),
        surface = Color(0xFF121D2E),
        onSurface = Color(0xFFE2E8F4),
        surfaceVariant = Color(0xFF303B4D),
        onSurfaceVariant = Color(0xFFC4C9D4),
    )

@Composable
public fun AtlasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
