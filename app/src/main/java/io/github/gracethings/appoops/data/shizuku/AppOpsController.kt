package io.github.gracethings.appoops.data.shizuku

import io.github.gracethings.appoops.data.model.OpMode
import io.github.gracethings.appoops.util.AppLogger
import io.github.gracethings.appoops.util.AppOpsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RuntimePermissionDenyUnsupportedException :
    UnsupportedOperationException("Runtime permissions only support ALLOW or IGNORE (revoked), not DENY")

class AppOpRejectedBySystemException(
    val requestedMode: OpMode,
    val actualMode: OpMode
) : IllegalStateException(
    "System rejected setting $requestedMode (remains $actualMode; target app may not declare the required permission)"
)

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

        val userId = (uid / 100000).coerceAtLeast(0)

        // Runtime-permission-backed AppOps (e.g. BODY_SENSORS, ACTIVITY_RECOGNITION) cannot be
        // modified directly via IAppOpsService.setMode/setUidMode on Android 14+; Android ties
        // their UID mode strictly to the PackageManager runtime permission grant state.
        if (AppOpsConstants.isRuntimePermissionOp(opCode)) {
            if (mode == OpMode.DENY) {
                return@withContext Result.failure(RuntimePermissionDenyUnsupportedException())
            }
            val permName = AppOpsConstants.getRuntimePermissionName(opCode)
                ?: return@withContext Result.failure(IllegalStateException("Missing runtime permission mapping for $opName"))
            val pmAction = if (mode == OpMode.ALLOW) "grant" else "revoke"
            val shellRes = runCatching {
                ShizukuShellExecutor.execute("pm $pmAction --user $userId $packageName $permName")
            }
            val actualMode = getMode(packageName, uid, opCode)
            val expectedValue = if (mode == OpMode.ALLOW) AppOpsConstants.MODE_ALLOWED else AppOpsConstants.MODE_IGNORED
            return@withContext if (actualMode.value == expectedValue) {
                AppLogger.d("Runtime perm $pmAction succeeded for $packageName ($opName -> $actualMode)")
                Result.success(Unit)
            } else {
                val errMsg = shellRes.getOrNull()?.error?.takeIf { it.isNotBlank() }
                AppLogger.w("Runtime perm $pmAction did not persist for $packageName ($opName): expected=$expectedValue actual=${actualMode.value} err=$errMsg")
                Result.failure(AppOpRejectedBySystemException(mode, actualMode))
            }
        }

        val targetModeValue = AppOpsConstants.resolveTargetModeValue(opCode, mode)
        val targetOpMode = OpMode.fromValue(targetModeValue)

        // 通道 1: 优先尝试官方 Binder IPC
        val binderResult = runCatching {
            AppOpsBinderEngine.setMode(opCode, uid, packageName, targetModeValue)
        }
        if (binderResult.isSuccess) {
            AppLogger.d("Binder setMode invoked for $packageName ($opName -> $targetOpMode)")
        } else {
            AppLogger.w("Binder setMode failed, falling back to shell: ${binderResult.exceptionOrNull()?.message}")

            // 通道 2: 平滑回退至特权 Shell
            val shellCmd1 = "cmd appops set --user $userId $packageName $opName ${targetOpMode.shellArg}"
            val shellCmd2 = "cmd appops set --user $userId --uid $uid $opName ${targetOpMode.shellArg}"
            val shellResult1 = runCatching { ShizukuShellExecutor.execute(shellCmd1) }
            val shellResult2 = runCatching { ShizukuShellExecutor.execute(shellCmd2) }

            val shellOk = (shellResult1.isSuccess && shellResult1.getOrThrow().exitCode == 0) ||
                (shellResult2.isSuccess && shellResult2.getOrThrow().exitCode == 0)
            if (!shellOk) {
                val err = shellResult1.exceptionOrNull()
                    ?: Exception("Shell returned exitCode: ${shellResult1.getOrNull()?.exitCode}")
                AppLogger.e("Failed to setMode for $packageName ($opName)", err)
                return@withContext Result.failure(err)
            }
            AppLogger.d("Shell setMode invoked for $packageName ($opName -> $targetOpMode)")
        }

        // Verify the OS actually persisted the requested effective mode
        val actualMode = getMode(packageName, uid, opCode)
        if (actualMode.value != targetModeValue) {
            AppLogger.w("AppOp change rejected/ignored by OS for $packageName ($opName): requested=$mode($targetModeValue), actual=$actualMode")
            return@withContext Result.failure(AppOpRejectedBySystemException(mode, actualMode))
        }

        Result.success(Unit)
    }

    suspend fun getMode(
        packageName: String,
        uid: Int,
        opCode: Int
    ): OpMode = withContext(Dispatchers.IO) {
        if (!ShizukuManager.isAuthorized) {
            return@withContext OpMode.fromValue(AppOpsConstants.getDefaultMode(opCode))
        }

        // 通道 1: 优先尝试官方 Binder 查询实际配置模式（综合 UID 与包级覆盖）
        val binderResult = runCatching {
            AppOpsBinderEngine.getOpMode(opCode, uid, packageName)?.let { OpMode.fromValue(it) }
        }.getOrNull()

        if (binderResult != null) {
            return@withContext binderResult
        }

        // 通道 2: Shell 回退查询
        val userId = (uid / 100000).coerceAtLeast(0)
        val opName = AppOpsConstants.getOpName(opCode)
        val defaultMode = AppOpsConstants.getDefaultMode(opCode)
        val shellRes = ShizukuShellExecutor.execute("cmd appops get --user $userId $packageName $opName")
        if (shellRes.exitCode == 0 && shellRes.output.isNotEmpty()) {
            var uidMode: OpMode? = null
            var pkgMode: OpMode? = null
            for (rawLine in shellRes.output.lines()) {
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty() ||
                    trimmed.startsWith("No operations", ignoreCase = true) ||
                    trimmed.startsWith("Default mode:", ignoreCase = true)
                ) {
                    continue
                }
                val parsed = parseModeToken(trimmed) ?: continue
                if (trimmed.startsWith("Uid mode:", ignoreCase = true)) {
                    uidMode = parsed
                } else {
                    pkgMode = parsed
                }
            }
            if (uidMode != null && uidMode.value != defaultMode) return@withContext uidMode
            if (pkgMode != null && pkgMode.value != defaultMode) return@withContext pkgMode
            if (uidMode != null) return@withContext uidMode
            if (pkgMode != null) return@withContext pkgMode
        }

        OpMode.fromValue(defaultMode)
    }

    private fun parseModeToken(line: String): OpMode? {
        val lower = line.lowercase()
        return when {
            lower.contains("ignore") -> OpMode.IGNORE
            lower.contains("deny") || lower.contains("error") -> OpMode.DENY
            lower.contains("allow") -> OpMode.ALLOW
            lower.contains("default") -> OpMode.DEFAULT
            else -> null
        }
    }

    /**
     * 一键禁用身体传感器与活动识别权限
     */
    suspend fun immunizeShakeAds(packageName: String, uid: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val targets = listOf(
            AppOpsConstants.OP_BODY_SENSORS to "BODY_SENSORS",
            AppOpsConstants.OP_ACTIVITY_RECOGNITION to "ACTIVITY_RECOGNITION"
        )
        var lastFailure: Throwable? = null
        targets.forEach { (code, name) ->
            val res = setMode(packageName, uid, code, name, OpMode.IGNORE)
            if (res.isFailure) lastFailure = res.exceptionOrNull()
        }

        if (lastFailure != null) Result.failure(lastFailure!!)
        else Result.success(Unit)
    }

    /**
     * Clipboard Shield (Read and Write)
     */
    suspend fun shieldClipboard(packageName: String, uid: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val r1 = setMode(packageName, uid, AppOpsConstants.OP_READ_CLIPBOARD, "READ_CLIPBOARD", OpMode.IGNORE)
        val r2 = setMode(packageName, uid, AppOpsConstants.OP_WRITE_CLIPBOARD, "WRITE_CLIPBOARD", OpMode.IGNORE)
        if (r1.isSuccess && r2.isSuccess) Result.success(Unit)
        else Result.failure(r1.exceptionOrNull() ?: r2.exceptionOrNull() ?: Exception("Failed to configure clipboard permissions"))
    }

    /**
     * Intercept Read Clipboard
     */
    suspend fun shieldReadClipboard(packageName: String, uid: Int): Result<Unit> = withContext(Dispatchers.IO) {
        setMode(packageName, uid, AppOpsConstants.OP_READ_CLIPBOARD, "READ_CLIPBOARD", OpMode.IGNORE)
    }

    /**
     * Intercept Write Clipboard
     */
    suspend fun shieldWriteClipboard(packageName: String, uid: Int): Result<Unit> = withContext(Dispatchers.IO) {
        setMode(packageName, uid, AppOpsConstants.OP_WRITE_CLIPBOARD, "WRITE_CLIPBOARD", OpMode.IGNORE)
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
        val r1 = setMode(packageName, uid, AppOpsConstants.OP_RUN_ANY_IN_BACKGROUND, "RUN_ANY_IN_BACKGROUND", OpMode.IGNORE)
        val r2 = setMode(packageName, uid, AppOpsConstants.OP_RUN_IN_BACKGROUND, "RUN_IN_BACKGROUND", OpMode.IGNORE)
        val r3 = setMode(packageName, uid, AppOpsConstants.OP_WAKE_LOCK, "WAKE_LOCK", OpMode.IGNORE)
        if (r1.isSuccess && r2.isSuccess && r3.isSuccess) Result.success(Unit)
        else Result.failure(r1.exceptionOrNull() ?: r2.exceptionOrNull() ?: r3.exceptionOrNull() ?: Exception("Failed to restrict background"))
    }

    /**
     * 恢复默认设置
     */
    suspend fun resetToDefault(packageName: String, uid: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = (uid / 100000).coerceAtLeast(0)
        val ops = listOf(
            AppOpsConstants.OP_BODY_SENSORS to "BODY_SENSORS",
            AppOpsConstants.OP_ACTIVITY_RECOGNITION to "ACTIVITY_RECOGNITION",
            AppOpsConstants.OP_READ_CLIPBOARD to "READ_CLIPBOARD",
            AppOpsConstants.OP_WRITE_CLIPBOARD to "WRITE_CLIPBOARD",
            AppOpsConstants.OP_QUERY_ALL_PACKAGES to "QUERY_ALL_PACKAGES",
            AppOpsConstants.OP_GET_USAGE_STATS to "GET_USAGE_STATS",
            AppOpsConstants.OP_RUN_ANY_IN_BACKGROUND to "RUN_ANY_IN_BACKGROUND",
            AppOpsConstants.OP_RUN_IN_BACKGROUND to "RUN_IN_BACKGROUND",
            AppOpsConstants.OP_WAKE_LOCK to "WAKE_LOCK",
            AppOpsConstants.OP_SYSTEM_ALERT_WINDOW to "SYSTEM_ALERT_WINDOW",
            AppOpsConstants.OP_WRITE_SETTINGS to "WRITE_SETTINGS",
            AppOpsConstants.OP_ACCESS_NOTIFICATIONS to "ACCESS_NOTIFICATIONS"
        )
        ops.forEach { (code, name) ->
            setMode(packageName, uid, code, name, OpMode.DEFAULT)
        }
        runCatching { ShizukuShellExecutor.execute("cmd appops reset --user $userId $packageName") }
        Result.success(Unit)
    }
}
