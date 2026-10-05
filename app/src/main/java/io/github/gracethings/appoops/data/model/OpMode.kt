package io.github.gracethings.appoops.data.model

import androidx.annotation.StringRes
import io.github.gracethings.appoops.R

enum class OpMode(
    val value: Int,
    val shellArg: String,
    val label: String,
    @StringRes val labelRes: Int
) {
    ALLOW(0, "allow", "Allow", R.string.mode_allow),
    IGNORE(1, "ignore", "Ignore", R.string.mode_ignore),
    DENY(2, "deny", "Deny", R.string.mode_deny),
    DEFAULT(3, "default", "Default", R.string.mode_default);

    companion object {
        fun fromValue(value: Int): OpMode = entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}

