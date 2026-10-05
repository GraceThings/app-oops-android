package io.github.gracethings.appoops.data.model

import android.graphics.drawable.Drawable

data class AppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val uid: Int,
    val isSystemApp: Boolean,
    val isShakeImmunized: Boolean = false,
    val isClipboardProtected: Boolean = false,
    val isBgRestricted: Boolean = false,
    val isQueryPackagesBlocked: Boolean = false,
    val interceptCount: Int = 0,
    val iconBitmap: androidx.compose.ui.graphics.ImageBitmap? = null
)

