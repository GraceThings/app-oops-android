package io.github.gracethings.appoops.data.shizuku

import io.github.gracethings.appoops.data.model.OpMode
import io.github.gracethings.appoops.util.AppLogger
import io.github.gracethings.appoops.util.AppOpsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppOpsController {

    suspend fun setMode(
        packageName: String,
        uid: Int,
        opCode: Int,
        opName: String,
        mode: OpMode
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!ShizukuManager.isAuthorized) {
            return@withContext Result.failure(IllegalStateException("Shizuku未授权"))
        }

        // 通道 1: 优先尝试官方 Binder IPC
        val binderResult = runCatching {
            AppOpsBinderEngine.setMode(opCode, uid, packageName, mode.value)
        }
        if (binderResult.isSuccess) {
            AppLogger.d("Binder setMode success for $packageName ($opName -> $mode)")
            return@withContext Result.success(Unit)
        }

        AppLogger.w("Binder setMode failed, falling back to shell: ${binderResult.exceptionOrNull()?.message}")

        // 通道 2: 平滑回退至特权 Shell
        val shellCmd1 = "cmd appops set $packageName $opName ${mode.shellArg}"
        val shellCmd2 = "cmd appops set --uid $uid $opName ${mode.shellArg}"
        val shellResult1 = runCatching { ShizukuShellExecutor.execute(shellCmd1) }
        val shellResult2 = runCatching { ShizukuShellExecutor.execute(shellCmd2) }

        if ((shellResult1.isSuccess && shellResult1.getOrThrow().exitCode == 0) ||
            (shellResult2.isSuccess && shellResult2.getOrThrow().exitCode == 0)) {
            AppLogger.d("Shell setMode success for $packageName ($opName -> $mode)")
            return@withContext Result.success(Unit)
        }

        val err = shellResult1.exceptionOrNull() ?: Exception("Shell returned exitCode: ${shellResult1.getOrNull()?.exitCode}")
        AppLogger.e("Failed to setMode for $packageName ($opName)", err)
        Result.failure(err)
    }

    suspend fun getMode(
        packageName: String,
        uid: Int,
        opCode: Int
    ): OpMode = withContext(Dispatchers.IO) {
        if (!ShizukuManager.isAuthorized) return@withContext OpMode.DEFAULT

        // 通道 1: 优先尝试官方 Binder 查询实际配置模式（非权限检查模式）
        val binderResult = runCatching {
            AppOpsBinderEngine.getOpMode(opCode, uid, packageName)?.let { OpMode.fromValue(it) }
        }.getOrNull()

        if (binderResult != null) {
            return@withContext binderResult
        }

        // 通道 2: Shell 回退查询
        val opName = AppOpsConstants.getOpName(opCode)
        val shellRes = ShizukuShellExecutor.execute("cmd appops get $packageName $opName")
        if (shellRes.exitCode == 0 && shellRes.output.isNotEmpty()) {
            val lower = shellRes.output.lowercase()
            return@withContext when {
                lower.contains("ignore") -> OpMode.IGNORE
                lower.contains("deny") || lower.contains("error") -> OpMode.DENY
                lower.contains("allow") -> OpMode.ALLOW
                else -> OpMode.DEFAULT
            }
        }

        OpMode.DEFAULT
    }

    /**
     * 一键禁用运动传感器权限
     * 同时封锁运动传感器、后台传感器、高采样率传感器与活动识别
     */
    suspend fun immunizeShakeAds(packageName: String, uid: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val targets = listOf(
            AppOpsConstants.OP_BODY_SENSORS to "BODY_SENSORS",
            AppOpsConstants.OP_BODY_SENSORS_BACKGROUND to "BODY_SENSORS_BACKGROUND",
            AppOpsConstants.OP_HIGH_SAMPLING_RATE_SENSORS to "HIGH_SAMPLING_RATE_SENSORS",
            AppOpsConstants.OP_ACTIVITY_RECOGNITION to "ACTIVITY_RECOGNITION"
        )
        var hasFail = false
        targets.forEach { (code, name) ->
            val res = setMode(packageName, uid, code, name, OpMode.IGNORE)
            if (res.isFailure) hasFail = true
        }

        if (hasFail) Result.failure(Exception("Failed to configure some sensor permissions"))
        else Result.success(Unit)
    }

    /**
     * Clipboard Shield
     */
    suspend fun shieldClipboard(packageName: String, uid: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val r1 = setMode(packageName, uid, AppOpsConstants.OP_READ_CLIPBOARD, "READ_CLIPBOARD", OpMode.IGNORE)
        val r2 = setMode(packageName, uid, AppOpsConstants.OP_WRITE_CLIPBOARD, "WRITE_CLIPBOARD", OpMode.IGNORE)
        if (r1.isSuccess && r2.isSuccess) Result.success(Unit)
        else Result.failure(Exception("Failed to configure clipboard permissions"))
    }

    /**
     * Block package scanning and usage stats
     */
    suspend fun blockQueryPackages(packageName: String, uid: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val r1 = setMode(packageName, uid, AppOpsConstants.OP_QUERY_ALL_PACKAGES, "QUERY_ALL_PACKAGES", OpMode.IGNORE)
        val r2 = setMode(packageName, uid, AppOpsConstants.OP_GET_USAGE_STATS, "GET_USAGE_STATS", OpMode.IGNORE)
        if (r1.isSuccess || r2.isSuccess) Result.success(Unit)
        else Result.failure(Exception("Failed to configure query package permissions"))
    }

    /**
     * Restrict background running and wake locks
     */
    suspend fun restrictBackground(packageName: String, uid: Int): Result<Unit> = withContext(Dispatchers.IO) {
        setMode(packageName, uid, AppOpsConstants.OP_RUN_IN_BACKGROUND, "RUN_IN_BACKGROUND", OpMode.IGNORE)
        setMode(packageName, uid, AppOpsConstants.OP_RUN_ANY_IN_BACKGROUND, "RUN_ANY_IN_BACKGROUND", OpMode.IGNORE)
        setMode(packageName, uid, AppOpsConstants.OP_WAKE_LOCK, "WAKE_LOCK", OpMode.IGNORE)
        // Try setting BOOT_COMPLETED
        ShizukuShellExecutor.execute("cmd appops set $packageName BOOT_COMPLETED ignore")
        Result.success(Unit)
    }

    /**
     * 恢复默认设置
     */
    suspend fun resetToDefault(packageName: String, uid: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val ops = listOf(
            AppOpsConstants.OP_BODY_SENSORS to "BODY_SENSORS",
            AppOpsConstants.OP_BODY_SENSORS_BACKGROUND to "BODY_SENSORS_BACKGROUND",
            AppOpsConstants.OP_HIGH_SAMPLING_RATE_SENSORS to "HIGH_SAMPLING_RATE_SENSORS",
            AppOpsConstants.OP_READ_CLIPBOARD to "READ_CLIPBOARD",
            AppOpsConstants.OP_WRITE_CLIPBOARD to "WRITE_CLIPBOARD",
            AppOpsConstants.OP_QUERY_ALL_PACKAGES to "QUERY_ALL_PACKAGES",
            AppOpsConstants.OP_GET_USAGE_STATS to "GET_USAGE_STATS",
            AppOpsConstants.OP_RUN_IN_BACKGROUND to "RUN_IN_BACKGROUND",
            AppOpsConstants.OP_WAKE_LOCK to "WAKE_LOCK",
            AppOpsConstants.OP_SYSTEM_ALERT_WINDOW to "SYSTEM_ALERT_WINDOW",
            AppOpsConstants.OP_WRITE_SETTINGS to "WRITE_SETTINGS",
            AppOpsConstants.OP_ACCESS_NOTIFICATIONS to "ACCESS_NOTIFICATIONS"
        )
        ops.forEach { (code, name) ->
            setMode(packageName, uid, code, name, OpMode.DEFAULT)
        }
        runCatching { ShizukuShellExecutor.execute("cmd appops reset $packageName") }
        Result.success(Unit)
    }
}

