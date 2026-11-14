package io.github.gracethings.appoops.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.gracethings.appoops.util.PackageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppIcon(
    packageName: String,
    contentDescription: String?,
    initialBitmap: ImageBitmap? = null,
    initialDrawable: Drawable? = null,
    size: Dp = 44.dp,
    cornerRadius: Dp = 12.dp,
    fallbackIconSize: Dp = 24.dp,
    iconPx: Int = 96,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val initialCached = initialBitmap ?: PackageUtils.getCachedAppIconBitmap(packageName)
    val iconBitmap by produceState(
        initialValue = initialCached,
        key1 = packageName,
        key2 = initialBitmap
    ) {
        if (value == null) {
            value = if (initialDrawable != null) {
                withContext(Dispatchers.IO) {
                    runCatching { initialDrawable.toBitmap(iconPx, iconPx).asImageBitmap() }.getOrNull()
                }
            } else {
                PackageUtils.loadAppIconBitmap(context, packageName, iconPx)
            }
        }
    }

    val bitmap = iconBitmap
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(fallbackIconSize)
            )
        }
    }
}
