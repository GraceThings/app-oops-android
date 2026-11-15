package io.github.gracethings.appoops.util

import android.Manifest
import android.app.AppOpsManager
import io.github.gracethings.appoops.data.model.OpMode

object AppOpsConstants {
    const val OP_COARSE_LOCATION = 0
    const val OP_FINE_LOCATION = 1
    const val OP_VIBRATE = 3
    const val OP_READ_CONTACTS = 4
    const val OP_WRITE_CONTACTS = 5
    const val OP_READ_CALL_LOG = 6
    const val OP_WRITE_CALL_LOG = 7

    const val OP_WRITE_SETTINGS = 23
    const val OP_SYSTEM_ALERT_WINDOW = 24
    const val OP_ACCESS_NOTIFICATIONS = 25
    const val OP_CAMERA = 26
    const val OP_RECORD_AUDIO = 27

    const val OP_READ_CLIPBOARD = 29
    const val OP_WRITE_CLIPBOARD = 30

    const val OP_WAKE_LOCK = 40
    const val OP_GET_USAGE_STATS = 43

    const val OP_BODY_SENSORS = 56
    const val OP_RUN_IN_BACKGROUND = 63
    const val OP_RUN_ANY_IN_BACKGROUND = 70
    const val OP_ACTIVITY_RECOGNITION = 79
    const val OP_QUERY_ALL_PACKAGES = 91

    // AppOps Modes
    const val MODE_ALLOWED = 0
    const val MODE_IGNORED = 1
    const val MODE_ERRORED = 2
    const val MODE_DEFAULT = 3

    private val defaultModeFallback = mapOf(
        OP_COARSE_LOCATION to MODE_ALLOWED,
        OP_FINE_LOCATION to MODE_ALLOWED,
        OP_VIBRATE to MODE_ALLOWED,
        OP_READ_CONTACTS to MODE_ALLOWED,
        OP_WRITE_CONTACTS to MODE_ALLOWED,
        OP_READ_CALL_LOG to MODE_ALLOWED,
        OP_WRITE_CALL_LOG to MODE_ALLOWED,
        OP_WRITE_SETTINGS to MODE_DEFAULT,
        OP_SYSTEM_ALERT_WINDOW to MODE_DEFAULT,
        OP_ACCESS_NOTIFICATIONS to MODE_DEFAULT,
        OP_CAMERA to MODE_ALLOWED,
        OP_RECORD_AUDIO to MODE_ALLOWED,
        OP_READ_CLIPBOARD to MODE_ALLOWED,
        OP_WRITE_CLIPBOARD to MODE_ALLOWED,
        OP_WAKE_LOCK to MODE_ALLOWED,
        OP_GET_USAGE_STATS to MODE_DEFAULT,
        OP_BODY_SENSORS to MODE_ALLOWED,
        OP_RUN_IN_BACKGROUND to MODE_ALLOWED,
        OP_RUN_ANY_IN_BACKGROUND to MODE_ALLOWED,
        OP_ACTIVITY_RECOGNITION to MODE_ALLOWED,
        OP_QUERY_ALL_PACKAGES to MODE_DEFAULT
    )

    private val runtimePermissions = mapOf(
        OP_BODY_SENSORS to Manifest.permission.BODY_SENSORS,
        OP_ACTIVITY_RECOGNITION to Manifest.permission.ACTIVITY_RECOGNITION
    )

    private val opNames = mapOf(
        OP_READ_CLIPBOARD to "READ_CLIPBOARD",
        OP_WRITE_CLIPBOARD to "WRITE_CLIPBOARD",
        OP_BODY_SENSORS to "BODY_SENSORS",
        OP_ACTIVITY_RECOGNITION to "ACTIVITY_RECOGNITION",
        OP_QUERY_ALL_PACKAGES to "QUERY_ALL_PACKAGES",
        OP_GET_USAGE_STATS to "GET_USAGE_STATS",
        OP_RUN_IN_BACKGROUND to "RUN_IN_BACKGROUND",
        OP_RUN_ANY_IN_BACKGROUND to "RUN_ANY_IN_BACKGROUND",
        OP_WAKE_LOCK to "WAKE_LOCK",
        OP_SYSTEM_ALERT_WINDOW to "SYSTEM_ALERT_WINDOW",
        OP_WRITE_SETTINGS to "WRITE_SETTINGS",
        OP_ACCESS_NOTIFICATIONS to "ACCESS_NOTIFICATIONS",
        OP_CAMERA to "CAMERA",
        OP_RECORD_AUDIO to "RECORD_AUDIO"
    )

    private val opChineseNames = mapOf(
        OP_READ_CLIPBOARD to "读取剪贴板",
        OP_WRITE_CLIPBOARD to "写入剪贴板",
        OP_BODY_SENSORS to "运动与身体传感器",
        OP_ACTIVITY_RECOGNITION to "健身与活动识别",
        OP_QUERY_ALL_PACKAGES to "读取已安装应用列表",
        OP_GET_USAGE_STATS to "获取应用使用情况统计",
        OP_RUN_IN_BACKGROUND to "后台运行权限",
        OP_RUN_ANY_IN_BACKGROUND to "任意后台活动",
        OP_WAKE_LOCK to "保持唤醒锁(防发热耗电)",
        OP_SYSTEM_ALERT_WINDOW to "显示在其他应用上层(悬浮窗)",
        OP_WRITE_SETTINGS to "修改系统设置",
        OP_ACCESS_NOTIFICATIONS to "访问全局通知消息",
        OP_CAMERA to "访问相机",
        OP_RECORD_AUDIO to "录音与麦克风"
    )

    val communityAllowedOps: Map<String, Int> = mapOf(
        "BODY_SENSORS" to OP_BODY_SENSORS,
        "ACTIVITY_RECOGNITION" to OP_ACTIVITY_RECOGNITION,
        "READ_CLIPBOARD" to OP_READ_CLIPBOARD,
        "WRITE_CLIPBOARD" to OP_WRITE_CLIPBOARD,
        "QUERY_ALL_PACKAGES" to OP_QUERY_ALL_PACKAGES,
        "GET_USAGE_STATS" to OP_GET_USAGE_STATS,
        "RUN_IN_BACKGROUND" to OP_RUN_IN_BACKGROUND,
        "RUN_ANY_IN_BACKGROUND" to OP_RUN_ANY_IN_BACKGROUND,
        "WAKE_LOCK" to OP_WAKE_LOCK,
        "SYSTEM_ALERT_WINDOW" to OP_SYSTEM_ALERT_WINDOW,
        "WRITE_SETTINGS" to OP_WRITE_SETTINGS,
        "ACCESS_NOTIFICATIONS" to OP_ACCESS_NOTIFICATIONS
    )

    val allManagedOps: IntArray by lazy {
        communityAllowedOps.values.distinct().toIntArray()
    }

    /**
     * Returns the OS default mode for [opCode]. Note that ops such as READ_CLIPBOARD or
     * RUN_ANY_IN_BACKGROUND default to MODE_ALLOWED (0), whereas special-access ops default
     * to MODE_DEFAULT (3).
     */
    fun getDefaultMode(opCode: Int): Int {
        val reflected = runCatching {
            val m = AppOpsManager::class.java.getMethod("opToDefaultMode", Int::class.javaPrimitiveType)
            m.invoke(null, opCode) as? Int
        }.getOrNull()
        if (reflected != null && reflected in MODE_ALLOWED..4) {
            return reflected
        }
        return defaultModeFallback[opCode] ?: MODE_DEFAULT
    }

    /**
     * Maps [OpMode.DEFAULT] to the actual system default mode for [opCode] so resetting an
     * op whose default is MODE_ALLOWED (0) does not write literal MODE_DEFAULT (3).
     */
    fun resolveTargetModeValue(opCode: Int, requestedMode: OpMode): Int {
        return if (requestedMode == OpMode.DEFAULT) {
            getDefaultMode(opCode)
        } else {
            requestedMode.value
        }
    }

    fun getRuntimePermissionName(opCode: Int): String? = runtimePermissions[opCode]

    fun isRuntimePermissionOp(opCode: Int): Boolean {
        if (runtimePermissions.containsKey(opCode)) return true
        return runCatching {
            val m = AppOpsManager::class.java.getMethod("opIsRuntimePermission", Int::class.javaPrimitiveType)
            m.invoke(null, opCode) as? Boolean ?: false
        }.getOrDefault(false)
    }

    fun getOpName(op: Int): String = opNames[op] ?: "OP_$op"

    fun getOpCode(opName: String): Int? =
        communityAllowedOps[opName.trim().uppercase()]

    fun getOpReadableName(op: Int): String = opChineseNames[op] ?: getOpName(op)

    fun getOpReadableName(context: android.content.Context, op: Int): String {
        val resId = when (op) {
            OP_BODY_SENSORS -> io.github.gracethings.appoops.R.string.op_body_sensors_title
            OP_ACTIVITY_RECOGNITION -> io.github.gracethings.appoops.R.string.op_activity_rec_title
            OP_READ_CLIPBOARD -> io.github.gracethings.appoops.R.string.op_read_clipboard_title
            OP_WRITE_CLIPBOARD -> io.github.gracethings.appoops.R.string.op_write_clipboard_title
            OP_QUERY_ALL_PACKAGES -> io.github.gracethings.appoops.R.string.op_query_packages_title
            OP_GET_USAGE_STATS -> io.github.gracethings.appoops.R.string.op_usage_stats_title
            OP_RUN_IN_BACKGROUND -> io.github.gracethings.appoops.R.string.op_run_in_bg_title
            OP_RUN_ANY_IN_BACKGROUND -> io.github.gracethings.appoops.R.string.op_run_any_in_bg_title
            OP_WAKE_LOCK -> io.github.gracethings.appoops.R.string.op_wake_lock_title
            OP_SYSTEM_ALERT_WINDOW -> io.github.gracethings.appoops.R.string.op_system_alert_title
            OP_WRITE_SETTINGS -> io.github.gracethings.appoops.R.string.op_write_settings_title
            OP_ACCESS_NOTIFICATIONS -> io.github.gracethings.appoops.R.string.op_access_notif_title
            else -> null
        }
        return resId?.let { context.getString(it) } ?: getOpName(op)
    }
}
