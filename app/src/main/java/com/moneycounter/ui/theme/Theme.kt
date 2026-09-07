package com.moneycounter.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = LuisoGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EFE3),
    onPrimaryContainer = LuisoGreen,
    secondary = LuisoGreenBright,
    onSecondary = Color(0xFF05200C),
    secondaryContainer = Color(0xFFD9F8E4),
    onSecondaryContainer = Color(0xFF0B3D1C),
    tertiary = LuisoYellow,
    onTertiary = LuisoInk,
    tertiaryContainer = Color(0xFFFFF3C4),
    onTertiaryContainer = Color(0xFF54461D),
    background = LuisoCream,
    onBackground = LuisoInk,
    surface = LuisoCream,
    onSurface = LuisoInk,
    surfaceVariant = Color(0xFFEFEFD8),
    onSurfaceVariant = Color(0xFF5A5A43),
    error = LuisoError,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9BE3B8),
    onPrimary = Color(0xFF0A3D20),
    primaryContainer = LuisoGreen,
    onPrimaryContainer = Color(0xFFD9EFE3),
    secondary = LuisoGreenBright,
    onSecondary = Color(0xFF05200C),
    secondaryContainer = Color(0xFF0E4A22),
    onSecondaryContainer = Color(0xFFB8F5CC),
    tertiary = LuisoYellow,
    onTertiary = Color(0xFF3D3A0F),
    tertiaryContainer = Color(0xFF4C4411),
    onTertiaryContainer = Color(0xFFFFE98A),
    background = Color(0xFF161712),
    onBackground = Color(0xFFE8E9DE),
    surface = Color(0xFF161712),
    onSurface = Color(0xFFE8E9DE),
    surfaceVariant = Color(0xFF4A4A38),
    onSurfaceVariant = Color(0xFFCCCBB0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun MoneyCounterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
