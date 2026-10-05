package io.github.gracethings.appoops.data.preference

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AlertNoticeType(val label: String) {
    TOAST("Toast 通知"),
    LIVE_NOTIFICATION("实时更新 (Android 16+)")
}

object AlertPreferences {
    private const val PREFS_NAME = "app_oops_alert_prefs"
    private const val KEY_ALERT_ENABLED = "key_alert_enabled"
    private const val KEY_NOTICE_TYPE = "key_notice_type"
    private const val KEY_GUARD_ENABLED = "key_guard_enabled"
    private const val KEY_TEMP_ALLOW_ENABLED = "key_temp_allow_enabled"
    private const val KEY_TEMP_ALLOW_DURATION = "key_temp_allow_duration"

    private val _isAlertEnabled = MutableStateFlow(true)
    val isAlertEnabled: StateFlow<Boolean> = _isAlertEnabled.asStateFlow()

    private val _noticeType = MutableStateFlow(AlertNoticeType.TOAST)
    val noticeType: StateFlow<AlertNoticeType> = _noticeType.asStateFlow()

    private val _isGuardEnabled = MutableStateFlow(true)
    val isGuardEnabled: StateFlow<Boolean> = _isGuardEnabled.asStateFlow()

    private val _isTempAllowEnabled = MutableStateFlow(true)
    val isTempAllowEnabled: StateFlow<Boolean> = _isTempAllowEnabled.asStateFlow()

    private val _tempAllowDuration = MutableStateFlow(10)
    val tempAllowDuration: StateFlow<Int> = _tempAllowDuration.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isAlertEnabled.value = prefs.getBoolean(KEY_ALERT_ENABLED, true)
        val typeStr = prefs.getString(KEY_NOTICE_TYPE, AlertNoticeType.TOAST.name) ?: AlertNoticeType.TOAST.name
        _noticeType.value = runCatching { AlertNoticeType.valueOf(typeStr) }.getOrDefault(AlertNoticeType.TOAST)
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

    fun setNoticeType(context: Context, type: AlertNoticeType) {
        _noticeType.value = type
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_NOTICE_TYPE, type.name)
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

