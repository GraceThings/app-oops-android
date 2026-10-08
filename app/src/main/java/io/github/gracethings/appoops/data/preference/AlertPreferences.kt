package io.github.gracethings.appoops.data.preference

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AlertPreferences {
    private const val PREFS_NAME = "app_oops_alert_prefs"
    private const val KEY_ALERT_ENABLED = "key_alert_enabled"
    private const val KEY_TOAST_ENABLED = "key_toast_enabled"
    private const val KEY_NOTIFICATION_ENABLED = "key_notification_enabled"
    private const val KEY_LIVE_UPDATES_ENABLED = "key_live_updates_enabled"
    private const val KEY_GUARD_ENABLED = "key_guard_enabled"
    private const val KEY_TEMP_ALLOW_ENABLED = "key_temp_allow_enabled"
    private const val KEY_TEMP_ALLOW_DURATION = "key_temp_allow_duration"

    // 默认关闭 (主开关)
    private val _isAlertEnabled = MutableStateFlow(false)
    val isAlertEnabled: StateFlow<Boolean> = _isAlertEnabled.asStateFlow()

    // 默认开启 (Toast 提示)
    private val _isToastEnabled = MutableStateFlow(true)
    val isToastEnabled: StateFlow<Boolean> = _isToastEnabled.asStateFlow()

    // 默认开启 (通知)
    private val _isNotificationEnabled = MutableStateFlow(true)
    val isNotificationEnabled: StateFlow<Boolean> = _isNotificationEnabled.asStateFlow()

    // 默认关闭 (实时更新 Android 16+)
    private val _isLiveUpdatesEnabled = MutableStateFlow(false)
    val isLiveUpdatesEnabled: StateFlow<Boolean> = _isLiveUpdatesEnabled.asStateFlow()

    private val _isGuardEnabled = MutableStateFlow(true)
    val isGuardEnabled: StateFlow<Boolean> = _isGuardEnabled.asStateFlow()

    private val _isTempAllowEnabled = MutableStateFlow(true)
    val isTempAllowEnabled: StateFlow<Boolean> = _isTempAllowEnabled.asStateFlow()

    private val _tempAllowDuration = MutableStateFlow(10)
    val tempAllowDuration: StateFlow<Int> = _tempAllowDuration.asStateFlow()

    fun isAndroid16Supported(): Boolean = Build.VERSION.SDK_INT >= 36

    fun isLiveUpdatesActive(): Boolean {
        return _isAlertEnabled.value &&
                _isNotificationEnabled.value &&
                _isLiveUpdatesEnabled.value &&
                isAndroid16Supported()
    }

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isAlertEnabled.value = prefs.getBoolean(KEY_ALERT_ENABLED, false)
        _isToastEnabled.value = prefs.getBoolean(KEY_TOAST_ENABLED, true)
        _isNotificationEnabled.value = prefs.getBoolean(KEY_NOTIFICATION_ENABLED, true)
        _isLiveUpdatesEnabled.value = if (isAndroid16Supported()) {
            prefs.getBoolean(KEY_LIVE_UPDATES_ENABLED, false)
        } else {
            false
        }
        _isGuardEnabled.value = prefs.getBoolean(KEY_GUARD_ENABLED, true)
        _isTempAllowEnabled.value = prefs.getBoolean(KEY_TEMP_ALLOW_ENABLED, true)
        _tempAllowDuration.value = prefs.getInt(KEY_TEMP_ALLOW_DURATION, 10)
    }

    fun setAlertEnabled(context: Context, enabled: Boolean) {
        _isAlertEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ALERT_ENABLED, enabled)
            .apply()
    }

    fun setToastEnabled(context: Context, enabled: Boolean) {
        _isToastEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_TOAST_ENABLED, enabled)
            .apply()
    }

    fun setNotificationEnabled(context: Context, enabled: Boolean) {
        _isNotificationEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_NOTIFICATION_ENABLED, enabled)
            .apply()
    }

    fun setLiveUpdatesEnabled(context: Context, enabled: Boolean) {
        val actual = if (isAndroid16Supported()) enabled else false
        _isLiveUpdatesEnabled.value = actual
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_LIVE_UPDATES_ENABLED, actual)
            .apply()
    }

    fun setGuardEnabled(context: Context, enabled: Boolean) {
        _isGuardEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_GUARD_ENABLED, enabled)
            .apply()
    }

    fun setTempAllowEnabled(context: Context, enabled: Boolean) {
        _isTempAllowEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_TEMP_ALLOW_ENABLED, enabled)
            .apply()
    }

    fun setTempAllowDuration(context: Context, seconds: Int) {
        _tempAllowDuration.value = seconds
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_TEMP_ALLOW_DURATION, seconds)
            .apply()
    }
}

