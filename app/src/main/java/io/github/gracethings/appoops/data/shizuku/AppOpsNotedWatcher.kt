package io.github.gracethings.appoops.data.shizuku

import io.github.gracethings.appoops.data.model.OpMode
import io.github.gracethings.appoops.service.InterceptionEventManager
import io.github.gracethings.appoops.util.AppLogger

object AppOpsNotedWatcher {
    private var isWatching = false
    private var notedBinder: AppOpsNotedCallbackBinder? = null
    private var startedBinder: AppOpsStartedCallbackBinder? = null

    fun startWatching(opsToWatch: IntArray) {
        if (isWatching) return
        try {
            val nBinder = AppOpsNotedCallbackBinder { op, uid, packageName, mode ->
                handleInterceptedCallback(op, uid, packageName, mode, source = "opNoted")
            }
            val sBinder = AppOpsStartedCallbackBinder { op, uid, packageName, mode ->
                handleInterceptedCallback(op, uid, packageName, mode, source = "opStarted")
            }

            val notedSuccess = AppOpsBinderEngine.startWatchingNoted(opsToWatch, nBinder)
            val startedSuccess = AppOpsBinderEngine.startWatchingStarted(opsToWatch, sBinder)

            if (notedSuccess || startedSuccess) {
                notedBinder = if (notedSuccess) nBinder else null
                startedBinder = if (startedSuccess) sBinder else null
                isWatching = true
                AppLogger.i("AppOpsWatcher registered for ${opsToWatch.size} ops (noted=$notedSuccess, started=$startedSuccess)")
            } else {
                AppLogger.w("AppOpsWatcher failed to register both noted and started callbacks")
            }
        } catch (e: Throwable) {
            AppLogger.e("Failed to start watching ops", e)
        }
    }

    private fun handleInterceptedCallback(
        op: Int,
        uid: Int,
        packageName: String,
        mode: Int,
        source: String
    ) {
        if (uid < android.os.Process.FIRST_APPLICATION_UID) {
            return
        }
        // mode == 1 is MODE_IGNORED, mode == 2 is MODE_ERRORED (Deny)
        if (mode == 1 || mode == 2) {
            AppLogger.d("$source blocked: op=$op, uid=$uid, pkg=$packageName, mode=$mode")
            InterceptionEventManager.onIntercepted(op, uid, packageName, OpMode.fromValue(mode))
        }
    }

    fun stopWatching() {
        if (!isWatching) return
        try {
            notedBinder?.let { AppOpsBinderEngine.stopWatchingNoted(it) }
            startedBinder?.let { AppOpsBinderEngine.stopWatchingStarted(it) }
            notedBinder = null
            startedBinder = null
            isWatching = false
            AppLogger.i("AppOpsNotedWatcher stopped")
        } catch (e: Throwable) {
            AppLogger.w("Failed to stop watching ops", e)
        }
    }

    fun reset() {
        notedBinder = null
        startedBinder = null
        isWatching = false
    }

    val isRunning: Boolean get() = isWatching
}
