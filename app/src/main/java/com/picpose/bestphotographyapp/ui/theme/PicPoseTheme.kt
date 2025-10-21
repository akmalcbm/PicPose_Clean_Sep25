package com.picpose.bestphotographyapp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBB86FC),
    secondary = Color(0xFF03DAC6),
    tertiary = Color(0xFF3700B3),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
    error = Color(0xFFCF6679)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6200EE),
    secondary = Color(0xFF03DAC6),
    tertiary = Color(0xFF3700B3),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF7F7F7),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    error = Color(0xFFB00020)
)

private val LightGlassyColorScheme = lightColorScheme(
    primary = Color(0xFF6200EE),
    surface = Color(0x99FFFFFF), // Semi-transparent white
    onSurface = Color(0xFF1C1B1F),
    background = Color(0xFFF0F0F0)
)

private val DarkGlassyColorScheme = darkColorScheme(
    primary = Color(0xFFBB86FC),
    surface = Color(0x991E1E1E), // Semi-transparent dark
    onSurface = Color(0xFFE0E0E0),
    background = Color(0xFF121212)
)

@Composable
fun PicPoseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useGlassyLook: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        useGlassyLook && darkTheme -> DarkGlassyColorScheme
        useGlassyLook -> LightGlassyColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
