package io.github.gracethings.appoops.data.repository

import android.content.Context
import io.github.gracethings.appoops.data.model.AppItem
import io.github.gracethings.appoops.data.model.OpMode
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

    private val _shakeImmunizedCount = MutableStateFlow(0)
    val shakeImmunizedCount: StateFlow<Int> = _shakeImmunizedCount.asStateFlow()

    private val _clipboardProtectedCount = MutableStateFlow(0)
    val clipboardProtectedCount: StateFlow<Int> = _clipboardProtectedCount.asStateFlow()

    private val _bgRestrictedCount = MutableStateFlow(0)
    val bgRestrictedCount: StateFlow<Int> = _bgRestrictedCount.asStateFlow()

    suspend fun loadApps(context: Context, forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        if (_appsFlow.value.isNotEmpty() && !forceRefresh) return@withContext
        _isLoading.value = true
        try {
            val baseApps = PackageUtils.getInstalledAppItems(context)
            if (!ShizukuManager.isAuthorized) {
                _appsFlow.value = baseApps
                _isInitialLoadCompleted.value = true
                _isLoading.value = false
                return@withContext
            }

            var shakeCount = 0
            var clipCount = 0
            var bgCount = 0

            // Sample top apps for status, or inspect on demand
            val enriched = baseApps.map { app ->
                val shakeMode = AppOpsController.getMode(app.packageName, app.uid, AppOpsConstants.OP_BODY_SENSORS)
                val clipMode = AppOpsController.getMode(app.packageName, app.uid, AppOpsConstants.OP_READ_CLIPBOARD)
                val bgMode = AppOpsController.getMode(app.packageName, app.uid, AppOpsConstants.OP_RUN_IN_BACKGROUND)
                val qMode = AppOpsController.getMode(app.packageName, app.uid, AppOpsConstants.OP_QUERY_ALL_PACKAGES)

                val isShake = shakeMode == OpMode.IGNORE || shakeMode == OpMode.DENY
                val isClip = clipMode == OpMode.IGNORE || clipMode == OpMode.DENY
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

            _shakeImmunizedCount.value = shakeCount
            _clipboardProtectedCount.value = clipCount
            _bgRestrictedCount.value = bgCount
            _appsFlow.value = enriched
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

