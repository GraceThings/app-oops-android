package io.github.gracethings.appoops.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import io.github.gracethings.appoops.data.model.AppItem
import java.util.concurrent.ConcurrentHashMap

object PackageUtils {
    private val appNameCache = ConcurrentHashMap<String, String>()

    fun getInstalledAppItems(context: Context): List<AppItem> {
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

            val appName = try {
                appInfo.loadLabel(pm).toString()
            } catch (_: Throwable) {
                packageInfo.packageName
            }
            appNameCache[packageInfo.packageName] = appName

            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val icon: Drawable? = try {
                appInfo.loadIcon(pm)
            } catch (_: Throwable) {
                null
            }
            val iconBitmap = try {
                icon?.toBitmap(96, 96)?.asImageBitmap()
            } catch (_: Throwable) {
                null
            }

            AppItem(
                packageName = packageInfo.packageName,
                appName = appName,
                icon = icon,
                uid = appInfo.uid,
                isSystemApp = isSystemApp,
                iconBitmap = iconBitmap
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

