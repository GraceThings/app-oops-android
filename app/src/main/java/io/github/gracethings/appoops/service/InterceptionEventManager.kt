package io.github.gracethings.appoops.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.github.gracethings.appoops.R
import io.github.gracethings.appoops.data.model.OpMode
import io.github.gracethings.appoops.data.preference.AlertPreferences
import io.github.gracethings.appoops.data.repository.AuditRepository
import io.github.gracethings.appoops.util.AppOpsConstants
import io.github.gracethings.appoops.util.PackageUtils
import java.util.concurrent.ConcurrentHashMap

data class DebouncedEvent(
    var count: Int,
    var lastTime: Long
)

object InterceptionEventManager {
    private var appContext: Context? = null
    private val debounceMap = ConcurrentHashMap<String, DebouncedEvent>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun onIntercepted(op: Int, uid: Int, packageName: String, mode: OpMode) {
        val ctx = appContext ?: return
        if (uid < android.os.Process.FIRST_APPLICATION_UID) {
            return
        }

        val resolvedPackage = if (packageName.isNotBlank()) {
            packageName
        } else {
            try {
                ctx.packageManager.getPackagesForUid(uid)?.firstOrNull() ?: ""
            } catch (_: Throwable) {
                ""
            }
        }

        if (resolvedPackage.isBlank()) return

        val appName = if (resolvedPackage == "io.github.gracethings.appoops.test") {
            ctx.getString(R.string.test_notice_app_name)
        } else {
            PackageUtils.getAppName(ctx, resolvedPackage)
        }
        val opReadableName = AppOpsConstants.getOpReadableName(ctx, op)
        val key = "$resolvedPackage-$op"

        val now = System.currentTimeMillis()
        val count = synchronized(debounceMap) {
            val existing = debounceMap[key]
            if (existing == null || now - existing.lastTime > 3000) {
                debounceMap[key] = DebouncedEvent(count = 1, lastTime = now)
                1
            } else {
                existing.count++
                existing.lastTime = now
                existing.count
            }
        }

        if (AlertPreferences.isAlertEnabled.value) {
            if (AlertPreferences.isToastEnabled.value) {
                val toastText = if (count == 1) {
                    ctx.getString(R.string.toast_blocked_single, appName, opReadableName)
                } else {
                    ctx.getString(R.string.toast_blocked_multi, appName, opReadableName, count)
                }
                mainHandler.post {
                    Toast.makeText(ctx, toastText, Toast.LENGTH_SHORT).show()
                }
            }
            if (AlertPreferences.isNotificationEnabled.value) {
                AppOpsMonitorService.updateNotification(
                    context = ctx,
                    packageName = resolvedPackage,
                    appName = appName,
                    opName = opReadableName,
                    count = count,
                    op = op,
                    uid = uid
                )
            }
        }

        AuditRepository.record(resolvedPackage, appName, op, opReadableName, now, 1, mode)
    }
}
