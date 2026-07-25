package com.example.ui.theme

import android.os.Build
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.ColorScheme

import androidx.compose.ui.graphics.Color

private val DarkColorSchemeDefault = darkColorScheme(
    primary = RosePrimary,
    secondary = PurpleAccent,
    tertiary = RoseDark,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Slate100,
    onSurface = Slate100,
    primaryContainer = RoseDark,
    onPrimaryContainer = Color.White,
)

private val LightColorSchemeDefault = lightColorScheme(
    primary = RosePrimary,
    secondary = PurpleAccent,
    tertiary = RoseDark,
    background = Color(0xFFFFF7F9),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
)

fun getColorsForTheme(themeName: String, isDark: Boolean): ColorScheme {
    val primary = when (themeName.uppercase()) {
        "RED" -> Color(0xFFFF2A43)
        "BLUE" -> Color(0xFF00B0FF)
        "GREEN" -> Color(0xFF00E676)
        "PURPLE" -> Color(0xFFD500F9)
        "ORANGE" -> Color(0xFFFF6D00)
        "PINK" -> Color(0xFFFF4081)
        else -> {
            if (themeName.startsWith("#")) {
                try {
                    val cleaned = themeName.substring(1)
                    val colorLong = cleaned.toLong(16)
                    if (cleaned.length == 6) {
                        Color(0xFF000000 or colorLong)
                    } else {
                        Color(colorLong)
                    }
                } catch (e: Exception) {
                    Color(0xFFFF2A43)
                }
            } else {
                Color(0xFFFF2A43)
            }
        }
    }

    val secondary = when (themeName.uppercase()) {
        "RED" -> Color(0xFFFF8A80)
        "BLUE" -> Color(0xFF82B1FF)
        "GREEN" -> Color(0xFFB9F6CA)
        "PURPLE" -> Color(0xFFEA80FC)
        "ORANGE" -> Color(0xFFFFD180)
        "PINK" -> Color(0xFFFF80AB)
        else -> {
            if (themeName.startsWith("#")) {
                val r = (primary.red * 255).toInt()
                val g = (primary.green * 255).toInt()
                val b = (primary.blue * 255).toInt()
                Color((r + 255) / 2, (g + 255) / 2, (b + 255) / 2)
            } else {
                Color(0xFFFF8A80)
            }
        }
    }

    val tertiary = when (themeName.uppercase()) {
        "RED" -> Color(0xFF8B0014)
        "BLUE" -> Color(0xFF006064)
        "GREEN" -> Color(0xFF1B5E20)
        "PURPLE" -> Color(0xFF4A148C)
        "ORANGE" -> Color(0xFFE65100)
        "PINK" -> Color(0xFF880E4F)
        else -> {
            if (themeName.startsWith("#")) {
                val r = (primary.red * 255).toInt()
                val g = (primary.green * 255).toInt()
                val b = (primary.blue * 255).toInt()
                Color((r * 0.5f).toInt(), (g * 0.5f).toInt(), (b * 0.5f).toInt())
            } else {
                Color(0xFF8B0014)
            }
        }
    }

    return if (isDark) {
        darkColorScheme(
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
            background = DarkBackground,
            surface = DarkSurface,
            onPrimary = Color.White,
            onSecondary = Color.Black,
            onBackground = Slate100,
            onSurface = Slate100,
            primaryContainer = tertiary,
            onPrimaryContainer = Color.White,
        )
    } else {
        lightColorScheme(
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
            background = Color(0xFFFFF7F9),
            surface = Color.White,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF1C1B1F),
            onSurface = Color(0xFF1C1B1F),
        )
    }
}

@Composable
fun JumpAnimeTheme(
    darkTheme: Boolean = true, // Default to dark as requested
    themeName: String = "RED",
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        themeName == "SYSTEM" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> getColorsForTheme(themeName, darkTheme)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
