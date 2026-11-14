package io.github.gracethings.appoops.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import io.github.gracethings.appoops.data.model.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object PackageUtils {
    private val appNameCache = ConcurrentHashMap<String, String>()
    private val iconBitmapCache = object : LruCache<String, ImageBitmap>(160) {}

    fun invalidatePackage(packageName: String) {
        appNameCache.remove(packageName)
        iconBitmapCache.remove(packageName)
    }

    fun getCachedAppIconBitmap(packageName: String): ImageBitmap? {
        return iconBitmapCache.get(packageName)
    }

    suspend fun loadAppIconBitmap(
        context: Context,
        packageName: String,
        sizePx: Int = 96
    ): ImageBitmap? {
        iconBitmapCache.get(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            iconBitmapCache.get(packageName)?.let { return@withContext it }
            val loaded = runCatching {
                context.packageManager
                    .getApplicationIcon(packageName)
                    .toBitmap(sizePx, sizePx)
                    .asImageBitmap()
            }.getOrNull()
            if (loaded != null) {
                iconBitmapCache.put(packageName, loaded)
            }
            loaded
        }
    }

    fun getInstalledAppItems(
        context: Context,
        cachedItems: Map<String, AppItem> = emptyMap()
    ): List<AppItem> {
        val pm = context.packageManager
        val packages = try {
            pm.getInstalledPackages(0)
        } catch (_: Throwable) {
            emptyList()
        }

        return packages.mapNotNull { packageInfo ->
            val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
            // Exclude our own app from being restricted accidentally
            if (packageInfo.packageName == context.packageName) return@mapNotNull null

            val cached = cachedItems[packageInfo.packageName]
            val appName = if (cached != null && cached.uid == appInfo.uid) {
                cached.appName
            } else {
                try {
                    appInfo.loadLabel(pm).toString()
                } catch (_: Throwable) {
                    packageInfo.packageName
                }
            }
            appNameCache[packageInfo.packageName] = appName

            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val cachedBitmap = if (cached != null && cached.uid == appInfo.uid) {
                cached.iconBitmap ?: iconBitmapCache.get(packageInfo.packageName)
            } else {
                iconBitmapCache.get(packageInfo.packageName)
            }

            AppItem(
                packageName = packageInfo.packageName,
                appName = appName,
                icon = cached?.takeIf { it.uid == appInfo.uid }?.icon,
                uid = appInfo.uid,
                isSystemApp = isSystemApp,
                iconBitmap = cachedBitmap
            )
        }.sortedWith(compareBy({ it.isSystemApp }, { it.appName.lowercase() }))
    }

    fun getAppName(context: Context, packageName: String): String {
        appNameCache[packageName]?.let { return it }
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val name = appInfo.loadLabel(pm).toString()
            appNameCache[packageName] = name
            name
        } catch (_: Throwable) {
            packageName
        }
    }

    fun getAppIcon(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: Throwable) {
            null
        }
    }
}
