package io.github.gracethings.appoops.data.model

import androidx.annotation.StringRes
import io.github.gracethings.appoops.R

enum class OpCategory(val title: String, @StringRes val titleRes: Int) {
    SENSOR("Sensors & Movement", R.string.category_sensor),
    PRIVACY("Clipboard & Privacy", R.string.category_privacy),
    BACKGROUND("Background & WakeLock", R.string.category_background),
    SYSTEM("System Alert & Security", R.string.category_system)
}

