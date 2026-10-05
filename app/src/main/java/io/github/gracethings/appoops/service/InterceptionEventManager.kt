package io.github.gracethings.appoops.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.github.gracethings.appoops.data.model.OpMode
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

        val appName = PackageUtils.getAppName(ctx, resolvedPackage)
        val opReadableName = AppOpsConstants.getOpReadableName(ctx, op)
        val key = "$resolvedPackage-$op"

        val now = System.currentTimeMillis()
        val existing = debounceMap[key]

        val alertEnabled = io.github.gracethings.appoops.data.preference.AlertPreferences.isAlertEnabled.value
        val alertType = io.github.gracethings.appoops.data.preference.AlertPreferences.noticeType.value

        if (existing == null || now - existing.lastTime > 3000) {
            // 首次或间隔超过3秒：根据用户设置提示并记录审计
            debounceMap[key] = DebouncedEvent(count = 1, lastTime = now)
            if (alertEnabled) {
                when (alertType) {
                    io.github.gracethings.appoops.data.preference.AlertNoticeType.TOAST -> {
                        mainHandler.post {
                            Toast.makeText(ctx, ctx.getString(io.github.gracethings.appoops.R.string.toast_blocked_single, appName, opReadableName), Toast.LENGTH_SHORT).show()
                        }
                        if (io.github.gracethings.appoops.data.preference.AlertPreferences.isTempAllowEnabled.value) {
                            AppOpsMonitorService.updateNotification(ctx, resolvedPackage, appName, opReadableName, 1, op, uid)
                        }
                    }
                    io.github.gracethings.appoops.data.preference.AlertNoticeType.LIVE_NOTIFICATION -> {
                        AppOpsMonitorService.updateNotification(ctx, resolvedPackage, appName, opReadableName, 1, op, uid)
                    }
                }
            }
            AuditRepository.record(resolvedPackage, appName, op, opReadableName, now, 1, mode)
        } else {
            // 3秒内高频调用：合并计数
            existing.count++
            existing.lastTime = now
            if (alertEnabled) {
                when (alertType) {
                    io.github.gracethings.appoops.data.preference.AlertNoticeType.TOAST -> {
                        mainHandler.post {
                            Toast.makeText(ctx, ctx.getString(io.github.gracethings.appoops.R.string.toast_blocked_multi, appName, opReadableName, existing.count), Toast.LENGTH_SHORT).show()
                        }
                        if (io.github.gracethings.appoops.data.preference.AlertPreferences.isTempAllowEnabled.value) {
                            AppOpsMonitorService.updateNotification(ctx, resolvedPackage, appName, opReadableName, existing.count, op, uid)
                        }
                    }
                    io.github.gracethings.appoops.data.preference.AlertNoticeType.LIVE_NOTIFICATION -> {
                        AppOpsMonitorService.updateNotification(ctx, resolvedPackage, appName, opReadableName, existing.count, op, uid)
                    }
                }
            }
            AuditRepository.record(resolvedPackage, appName, op, opReadableName, now, 1, mode)
        }
    }
}

