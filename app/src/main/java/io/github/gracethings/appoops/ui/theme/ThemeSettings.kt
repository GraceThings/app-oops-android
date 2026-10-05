package io.github.gracethings.appoops.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import androidx.annotation.StringRes
import io.github.gracethings.appoops.R

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class ThemeAccent(val label: String, @StringRes val labelRes: Int, val color: Color) {
    DYNAMIC("Dynamic (Monet)", R.string.setting_color_dynamic, Color(0xFF006C4C)),
    JADE("Jade", R.string.setting_color_jade, VibrantGreen),
    COBALT("Cobalt", R.string.setting_color_cobalt, VibrantBlue),
    AMETHYST("Amethyst", R.string.setting_color_amethyst, VibrantPurple),
    AMBER("Amber", R.string.setting_color_amber, VibrantAmber)
}

object ThemePreferences {
    private const val PREFS_NAME = "app_oops_theme_prefs"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_ACCENT = "theme_accent"

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _themeAccent = MutableStateFlow(ThemeAccent.DYNAMIC)
    val themeAccent: StateFlow<ThemeAccent> = _themeAccent.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val modeStr = prefs.getString(KEY_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val accentStr = prefs.getString(KEY_ACCENT, ThemeAccent.DYNAMIC.name) ?: ThemeAccent.DYNAMIC.name

        _themeMode.value = runCatching { ThemeMode.valueOf(modeStr) }.getOrDefault(ThemeMode.SYSTEM)
        _themeAccent.value = runCatching { ThemeAccent.valueOf(accentStr) }.getOrDefault(ThemeAccent.DYNAMIC)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, mode.name).apply()
    }

    fun setThemeAccent(context: Context, accent: ThemeAccent) {
        _themeAccent.value = accent
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACCENT, accent.name).apply()
    }
}

