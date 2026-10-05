package io.github.gracethings.appoops.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun AppOopsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    themeAccent: ThemeAccent = ThemeAccent.DYNAMIC,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        themeAccent == ThemeAccent.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            val primaryColor = themeAccent.color
            if (darkTheme) {
                darkColorScheme(
                    primary = primaryColor,
                    secondary = SecondaryDark,
                    tertiary = TertiaryDark,
                    background = BackgroundDark,
                    surface = SurfaceDark,
                    error = ErrorDark
                )
            } else {
                lightColorScheme(
                    primary = primaryColor,
                    secondary = SecondaryLight,
                    tertiary = TertiaryLight,
                    background = BackgroundLight,
                    surface = SurfaceLight,
                    error = ErrorLight
                )
            }
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

