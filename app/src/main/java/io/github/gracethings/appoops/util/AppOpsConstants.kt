package io.github.gracethings.appoops.util

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
    const val OP_BODY_SENSORS_BACKGROUND = 107
    const val OP_HIGH_SAMPLING_RATE_SENSORS = 117

    // AppOps Modes
    const val MODE_ALLOWED = 0
    const val MODE_IGNORED = 1
    const val MODE_ERRORED = 2
    const val MODE_DEFAULT = 3

    private val opNames = mapOf(
        OP_READ_CLIPBOARD to "READ_CLIPBOARD",
        OP_WRITE_CLIPBOARD to "WRITE_CLIPBOARD",
        OP_BODY_SENSORS to "BODY_SENSORS",
        OP_BODY_SENSORS_BACKGROUND to "BODY_SENSORS_BACKGROUND",
        OP_HIGH_SAMPLING_RATE_SENSORS to "HIGH_SAMPLING_RATE_SENSORS",
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
        OP_BODY_SENSORS_BACKGROUND to "后台身体传感器",
        OP_HIGH_SAMPLING_RATE_SENSORS to "高采样率运动传感器(陀螺仪/加速度)",
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
        "BODY_SENSORS_BACKGROUND" to OP_BODY_SENSORS_BACKGROUND,
        "HIGH_SAMPLING_RATE_SENSORS" to OP_HIGH_SAMPLING_RATE_SENSORS,
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

    fun getOpName(op: Int): String = opNames[op] ?: "OP_$op"

    fun getOpCode(opName: String): Int? =
        communityAllowedOps[opName.trim().uppercase()]

    fun getOpReadableName(op: Int): String = opChineseNames[op] ?: getOpName(op)

    fun getOpReadableName(context: android.content.Context, op: Int): String {
        val resId = when (op) {
            OP_BODY_SENSORS -> io.github.gracethings.appoops.R.string.op_body_sensors_title
            OP_HIGH_SAMPLING_RATE_SENSORS -> io.github.gracethings.appoops.R.string.op_high_sampling_sensors_title
            OP_BODY_SENSORS_BACKGROUND -> io.github.gracethings.appoops.R.string.op_body_sensors_bg_title
            OP_ACTIVITY_RECOGNITION -> io.github.gracethings.appoops.R.string.op_activity_rec_title
            OP_READ_CLIPBOARD -> io.github.gracethings.appoops.R.string.op_read_clipboard_title
            OP_WRITE_CLIPBOARD -> io.github.gracethings.appoops.R.string.op_write_clipboard_title
            OP_QUERY_ALL_PACKAGES -> io.github.gracethings.appoops.R.string.op_query_packages_title
            OP_GET_USAGE_STATS -> io.github.gracethings.appoops.R.string.op_usage_stats_title
            OP_RUN_IN_BACKGROUND -> io.github.gracethings.appoops.R.string.op_run_in_bg_title
            OP_WAKE_LOCK -> io.github.gracethings.appoops.R.string.op_wake_lock_title
            OP_SYSTEM_ALERT_WINDOW -> io.github.gracethings.appoops.R.string.op_system_alert_title
            OP_WRITE_SETTINGS -> io.github.gracethings.appoops.R.string.op_write_settings_title
            OP_ACCESS_NOTIFICATIONS -> io.github.gracethings.appoops.R.string.op_access_notif_title
            else -> null
        }
        return resId?.let { context.getString(it) } ?: getOpName(op)
    }
}

