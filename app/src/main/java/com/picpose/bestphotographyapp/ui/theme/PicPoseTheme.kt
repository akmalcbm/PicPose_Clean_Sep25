package com.picpose.bestphotographyapp.ui.theme

import android.app.Activity
import android.os.Build
import android.view.Window
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
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useGlassyLook && darkTheme -> DarkGlassyColorScheme
        useGlassyLook -> LightGlassyColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val statusBarColor = colorScheme.background.toArgb()
            val navigationBarColor = colorScheme.background.toArgb()

            // ✅ Method 1: Check for available method at runtime
            try {
                // Try to use WindowCompat if available
                val setStatusBarColorMethod = WindowCompat::class.java.getMethod(
                    "setStatusBarColor",
                    Window::class.java,
                    Int::class.java
                )
                setStatusBarColorMethod.invoke(null, window, statusBarColor)

                val setNavBarColorMethod = WindowCompat::class.java.getMethod(
                    "setNavigationBarColor",
                    Window::class.java,
                    Int::class.java
                )
                setNavBarColorMethod.invoke(null, window, navigationBarColor)
            } catch (e: Exception) {
                // Fallback to direct window properties with suppression
                @Suppress("DEPRECATION")
                window.statusBarColor = statusBarColor
                @Suppress("DEPRECATION")
                window.navigationBarColor = navigationBarColor
            }

            // WindowInsetsController works fine
            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            windowInsetsController.isAppearanceLightStatusBars = !darkTheme
            windowInsetsController.isAppearanceLightNavigationBars = !darkTheme

            // Enable edge-to-edge
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}