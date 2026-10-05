package io.github.gracethings.appoops.data.shizuku

import io.github.gracethings.appoops.data.model.OpMode
import io.github.gracethings.appoops.service.InterceptionEventManager
import io.github.gracethings.appoops.util.AppLogger

object AppOpsNotedWatcher {
    private var isWatching = false
    private var callbackBinder: AppOpsNotedCallbackBinder? = null

    fun startWatching(opsToWatch: IntArray) {
        if (isWatching) return
        try {
            val binder = AppOpsNotedCallbackBinder { op, uid, packageName, mode ->
                if (uid < android.os.Process.FIRST_APPLICATION_UID) {
                    return@AppOpsNotedCallbackBinder
                }
                // mode == 1 is MODE_IGNORED, mode == 2 is MODE_ERRORED (Deny)
                if (mode == 1 || mode == 2) {
                    AppLogger.d("opNoted blocked: op=$op, uid=$uid, pkg=$packageName, mode=$mode")
                    InterceptionEventManager.onIntercepted(op, uid, packageName, OpMode.fromValue(mode))
                }
            }
            val success = AppOpsBinderEngine.startWatchingNoted(opsToWatch, binder)
            if (success) {
                callbackBinder = binder
                isWatching = true
                AppLogger.i("AppOpsNotedWatcher successfully registered for ${opsToWatch.size} ops")
            } else {
                AppLogger.w("AppOpsNotedWatcher startWatchingNoted returned false")
            }
        } catch (e: Throwable) {
            AppLogger.e("Failed to start watching noted ops", e)
        }
    }

    fun stopWatching() {
        if (!isWatching) return
        try {
            callbackBinder?.let {
                AppOpsBinderEngine.stopWatchingNoted(it)
            }
            callbackBinder = null
            isWatching = false
            AppLogger.i("AppOpsNotedWatcher stopped")
        } catch (e: Throwable) {
            AppLogger.w("Failed to stop watching noted ops", e)
        }
    }

    val isRunning: Boolean get() = isWatching
}

