package io.github.gracethings.appoops.service

import android.content.Context
import android.widget.Toast
import io.github.gracethings.appoops.R
import io.github.gracethings.appoops.data.model.OpMode
import io.github.gracethings.appoops.data.shizuku.AppOpsController
import io.github.gracethings.appoops.util.AppLogger
import io.github.gracethings.appoops.util.AppOpsConstants
import io.github.gracethings.appoops.util.PackageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object TempAllowManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeRevertJobs = ConcurrentHashMap<String, Job>()

    fun temporaryAllow(
        context: Context,
        packageName: String,
        uid: Int,
        op: Int,
        durationSeconds: Int
    ) {
        val key = "$packageName-$op"
        activeRevertJobs[key]?.cancel()

        scope.launch {
            try {
                val appName = if (packageName == "io.github.gracethings.appoops.test") {
                    context.getString(R.string.test_notice_app_name)
                } else {
                    PackageUtils.getAppName(context, packageName)
                }
                val opName = AppOpsConstants.getOpName(op)
                val opReadable = AppOpsConstants.getOpReadableName(context, op)

                // 1. Set mode to ALLOW
                if (packageName != "io.github.gracethings.appoops.test") {
                    AppOpsController.setMode(packageName, uid, op, opName, OpMode.ALLOW)
                }
                AppLogger.i("Temporarily allowed $packageName ($uid) op $op for ${durationSeconds}s")

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_temp_allow_granted, appName, opReadable, durationSeconds),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // 2. Countdown loop with live progress updates (Live Updates / ProgressStyle)
                for (remaining in durationSeconds downTo 1) {
                    if (io.github.gracethings.appoops.data.preference.AlertPreferences.isLiveUpdatesActive()) {
                        AppOpsMonitorService.updateCountdownNotification(
                            context = context,
                            packageName = packageName,
                            appName = appName,
                            opName = opReadable,
                            remainingSeconds = remaining,
                            totalSeconds = durationSeconds,
                            op = op,
                            uid = uid
                        )
                    }
                    delay(1000L)
                }

                // 3. Revert back to IGNORE
                if (packageName != "io.github.gracethings.appoops.test") {
                    AppOpsController.setMode(packageName, uid, op, opName, OpMode.IGNORE)
                }
                AppLogger.i("Reverted $packageName ($uid) op $op back to IGNORE")
                androidx.core.app.NotificationManagerCompat.from(context).cancel(AppOpsMonitorService.ALERT_NOTIFICATION_ID)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_temp_allow_expired, appName, opReadable),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Throwable) {
                AppLogger.e("Failed in temporaryAllow", e)
            } finally {
                activeRevertJobs.remove(key)
            }
        }.also { activeRevertJobs[key] = it }
    }

    fun cancelAndRevertAll(context: Context) {
        activeRevertJobs.forEach { (key, job) ->
            job.cancel()
            val parts = key.split("-")
            if (parts.size == 2) {
                val pkg = parts[0]
                val opCode = parts[1].toIntOrNull() ?: return@forEach
                if (pkg != "io.github.gracethings.appoops.test") {
                    val opName = AppOpsConstants.getOpName(opCode)
                    scope.launch {
                        val uid = runCatching { context.packageManager.getApplicationInfo(pkg, 0).uid }.getOrDefault(0)
                        AppOpsController.setMode(pkg, uid, opCode, opName, OpMode.IGNORE)
                    }
                }
            }
        }
        activeRevertJobs.clear()
        androidx.core.app.NotificationManagerCompat.from(context).cancel(AppOpsMonitorService.ALERT_NOTIFICATION_ID)
    }
}

