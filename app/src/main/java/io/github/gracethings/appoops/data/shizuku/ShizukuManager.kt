package io.github.gracethings.appoops.data.shizuku

import android.content.pm.PackageManager
import io.github.gracethings.appoops.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

object ShizukuManager {
    const val REQUEST_CODE_PERMISSION = 1001

    private val _status = MutableStateFlow(ShizukuState.NOT_RUNNING)
    val status: StateFlow<ShizukuState> = _status.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        AppLogger.i("Shizuku binder received")
        updateState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        AppLogger.w("Shizuku binder dead")
        _status.value = ShizukuState.DEAD
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_PERMISSION) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                AppLogger.i("Shizuku permission granted")
                _status.value = ShizukuState.AUTHORIZED
            } else {
                AppLogger.w("Shizuku permission denied")
                updateState()
            }
        }
    }

    fun init() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        updateState()
    }

    fun destroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }

    fun updateState() {
        if (Shizuku.isPreV11() || !Shizuku.pingBinder()) {
            _status.value = ShizukuState.NOT_RUNNING
            return
        }

        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                _status.value = ShizukuState.AUTHORIZED
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                _status.value = ShizukuState.RATIONALE
            } else {
                _status.value = ShizukuState.NEEDS_PERMISSION
            }
        } catch (e: Throwable) {
            AppLogger.e("Failed to check Shizuku permission", e)
            _status.value = ShizukuState.NOT_RUNNING
        }
    }

    fun requestPermission(code: Int = REQUEST_CODE_PERMISSION) {
        if (_status.value == ShizukuState.NEEDS_PERMISSION || _status.value == ShizukuState.RATIONALE) {
            try {
                Shizuku.requestPermission(code)
            } catch (e: Throwable) {
                AppLogger.e("Failed to request Shizuku permission", e)
            }
        }
    }

    val isAuthorized: Boolean
        get() = _status.value == ShizukuState.AUTHORIZED
}

