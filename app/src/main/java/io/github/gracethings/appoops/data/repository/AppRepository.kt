package io.github.gracethings.appoops.data.repository

import android.content.Context
import io.github.gracethings.appoops.data.model.AppItem
import io.github.gracethings.appoops.data.model.OpMode
import io.github.gracethings.appoops.data.shizuku.AppOpsBinderEngine
import io.github.gracethings.appoops.data.shizuku.AppOpsController
import io.github.gracethings.appoops.data.shizuku.ShizukuManager
import io.github.gracethings.appoops.util.AppLogger
import io.github.gracethings.appoops.util.AppOpsConstants
import io.github.gracethings.appoops.util.PackageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

object AppRepository {
    private val _appsFlow = MutableStateFlow<List<AppItem>>(emptyList())
    val appsFlow: StateFlow<List<AppItem>> = _appsFlow.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isInitialLoadCompleted = MutableStateFlow(false)
    val isInitialLoadCompleted: StateFlow<Boolean> = _isInitialLoadCompleted.asStateFlow()

    private val _isEnriched = MutableStateFlow(false)
    val isEnriched: StateFlow<Boolean> = _isEnriched.asStateFlow()

    private val _shakeImmunizedCount = MutableStateFlow(0)
    val shakeImmunizedCount: StateFlow<Int> = _shakeImmunizedCount.asStateFlow()

    private val _clipboardProtectedCount = MutableStateFlow(0)
    val clipboardProtectedCount: StateFlow<Int> = _clipboardProtectedCount.asStateFlow()

    private val _bgRestrictedCount = MutableStateFlow(0)
    val bgRestrictedCount: StateFlow<Int> = _bgRestrictedCount.asStateFlow()

    suspend fun loadApps(context: Context, forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        if (_appsFlow.value.isNotEmpty() && _isEnriched.value && !forceRefresh) return@withContext
        _isLoading.value = true
        try {
            val baseApps = if (_appsFlow.value.isNotEmpty()) {
                _appsFlow.value
            } else {
                PackageUtils.getInstalledAppItems(context)
            }

            if (!ShizukuManager.isAuthorized) {
                _appsFlow.value = baseApps
                _isInitialLoadCompleted.value = true
                _isLoading.value = false
                _isEnriched.value = false
                return@withContext
            }

            // High-speed batch query via IAppOpsService.getPackagesForOps (1 single IPC call)
            val targetOps = intArrayOf(
                AppOpsConstants.OP_BODY_SENSORS,
                AppOpsConstants.OP_READ_CLIPBOARD,
                AppOpsConstants.OP_WRITE_CLIPBOARD,
                AppOpsConstants.OP_RUN_IN_BACKGROUND,
                AppOpsConstants.OP_QUERY_ALL_PACKAGES
            )
            val batchOverrides = AppOpsBinderEngine.getOverriddenOpsForPackages(targetOps)

            var shakeCount = 0
            var clipCount = 0
            var bgCount = 0

            val enriched = if (batchOverrides != null) {
                baseApps.map { app ->
                    val appOps = batchOverrides[app.packageName] ?: emptyMap()
                    val shakeModeVal = appOps[AppOpsConstants.OP_BODY_SENSORS]
                    val clipReadModeVal = appOps[AppOpsConstants.OP_READ_CLIPBOARD]
                    val clipWriteModeVal = appOps[AppOpsConstants.OP_WRITE_CLIPBOARD]
                    val bgModeVal = appOps[AppOpsConstants.OP_RUN_IN_BACKGROUND]
                    val qModeVal = appOps[AppOpsConstants.OP_QUERY_ALL_PACKAGES]

                    val isShake = shakeModeVal == AppOpsConstants.MODE_IGNORED || shakeModeVal == AppOpsConstants.MODE_ERRORED
                    val isClipRead = clipReadModeVal == AppOpsConstants.MODE_IGNORED || clipReadModeVal == AppOpsConstants.MODE_ERRORED
                    val isClipWrite = clipWriteModeVal == AppOpsConstants.MODE_IGNORED || clipWriteModeVal == AppOpsConstants.MODE_ERRORED
                    val isClip = isClipRead || isClipWrite
                    val isBg = bgModeVal == AppOpsConstants.MODE_IGNORED || bgModeVal == AppOpsConstants.MODE_ERRORED
                    val isQ = qModeVal == AppOpsConstants.MODE_IGNORED || qModeVal == AppOpsConstants.MODE_ERRORED

                    if (isShake) shakeCount++
                    if (isClip) clipCount++
                    if (isBg) bgCount++

                    app.copy(
                        isShakeImmunized = isShake,
                        isClipboardProtected = isClip,
                        isBgRestricted = isBg,
                        isQueryPackagesBlocked = isQ
                    )
                }
            } else {
                // Smooth fallback: per-app queries
                baseApps.map { app ->
                    val shakeMode = AppOpsController.getMode(app.packageName, app.uid, AppOpsConstants.OP_BODY_SENSORS)
                    val clipReadMode = AppOpsController.getMode(app.packageName, app.uid, AppOpsConstants.OP_READ_CLIPBOARD)
                    val clipWriteMode = AppOpsController.getMode(app.packageName, app.uid, AppOpsConstants.OP_WRITE_CLIPBOARD)
                    val bgMode = AppOpsController.getMode(app.packageName, app.uid, AppOpsConstants.OP_RUN_IN_BACKGROUND)
                    val qMode = AppOpsController.getMode(app.packageName, app.uid, AppOpsConstants.OP_QUERY_ALL_PACKAGES)

                    val isShake = shakeMode == OpMode.IGNORE || shakeMode == OpMode.DENY
                    val isClipRead = clipReadMode == OpMode.IGNORE || clipReadMode == OpMode.DENY
                    val isClipWrite = clipWriteMode == OpMode.IGNORE || clipWriteMode == OpMode.DENY
                    val isClip = isClipRead || isClipWrite
                    val isBg = bgMode == OpMode.IGNORE || bgMode == OpMode.DENY
                    val isQ = qMode == OpMode.IGNORE || qMode == OpMode.DENY

                    if (isShake) shakeCount++
                    if (isClip) clipCount++
                    if (isBg) bgCount++

                    app.copy(
                        isShakeImmunized = isShake,
                        isClipboardProtected = isClip,
                        isBgRestricted = isBg,
                        isQueryPackagesBlocked = isQ
                    )
                }
            }

            _shakeImmunizedCount.value = shakeCount
            _clipboardProtectedCount.value = clipCount
            _bgRestrictedCount.value = bgCount
            _appsFlow.value = enriched
            _isEnriched.value = true
        } catch (e: Throwable) {
            AppLogger.e("Failed to load apps", e)
        } finally {
            _isInitialLoadCompleted.value = true
            _isLoading.value = false
        }
    }

    fun updateAppItem(updated: AppItem) {
        val current = _appsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.packageName == updated.packageName }
        if (index >= 0) {
            current[index] = updated
            _appsFlow.value = current
            recomputeCounts(current)
        }
    }

    private fun recomputeCounts(list: List<AppItem>) {
        _shakeImmunizedCount.value = list.count { it.isShakeImmunized }
        _clipboardProtectedCount.value = list.count { it.isClipboardProtected }
        _bgRestrictedCount.value = list.count { it.isBgRestricted }
    }
}

