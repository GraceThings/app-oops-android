package io.github.gracethings.appoops.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.gracethings.appoops.MainActivity
import io.github.gracethings.appoops.R
import io.github.gracethings.appoops.data.preference.AlertPreferences
import io.github.gracethings.appoops.data.shizuku.AppOpsNotedWatcher
import io.github.gracethings.appoops.data.shizuku.ShizukuManager
import io.github.gracethings.appoops.data.shizuku.ShizukuState
import io.github.gracethings.appoops.receiver.TempAllowReceiver
import io.github.gracethings.appoops.util.AppLogger
import io.github.gracethings.appoops.util.AppOpsConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppOpsMonitorService : Service {
    constructor() : super()

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var shizukuObserverJob: Job? = null

    companion object {
        const val FOREGROUND_CHANNEL_ID = "appops_monitor_channel_v1"
        const val ALERT_CHANNEL_LIVE_ID = "appops_live_chip_v7"
        const val ALERT_CHANNEL_TOAST_ID = "appops_standard_alert_v7"

        const val FOREGROUND_NOTIFICATION_ID = 2001
        const val ALERT_NOTIFICATION_ID = 2002
        private const val ALERT_NOTIFICATION_ID_BASE = 2100
        private const val ALERT_GROUP_KEY = "appops_interception_alerts"

        const val ACTION_START = "io.github.gracethings.appoops.service.ACTION_START"
        const val ACTION_STOP = "io.github.gracethings.appoops.service.ACTION_STOP"
        const val ACTION_INSPECT = "io.github.gracethings.appoops.ACTION_INSPECT"
        const val EXTRA_PACKAGE = "extra_package"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun getAlertNotificationId(packageName: String, op: Int): Int {
            val hash = ((packageName.hashCode() * 31) + op) and 0x7FFFFFFF
            return ALERT_NOTIFICATION_ID_BASE + (hash % 1000)
        }

        fun startService(context: Context) {
            val intent = Intent(context, AppOpsMonitorService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AppOpsMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun ensureNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

                // 1. Silent foreground service channel
                if (nm.getNotificationChannel(FOREGROUND_CHANNEL_ID) == null) {
                    val fgChannel = NotificationChannel(
                        FOREGROUND_CHANNEL_ID,
                        context.getString(R.string.monitor_notif_channel),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = context.getString(R.string.monitor_notif_channel_desc)
                    }
                    nm.createNotificationChannel(fgChannel)
                }

                // Clean up obsolete channels
                nm.deleteNotificationChannel("appops_live_chip_v5")
                nm.deleteNotificationChannel("appops_standard_alert_v6")

                // 2. Live chip alert channel (Android 16+ Promoted Ongoing pill + pop up on screen, Default importance)
                if (nm.getNotificationChannel(ALERT_CHANNEL_LIVE_ID) == null) {
                    val liveChannel = NotificationChannel(
                        ALERT_CHANNEL_LIVE_ID,
                        context.getString(R.string.monitor_notif_live_channel),
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = context.getString(R.string.monitor_notif_channel_desc)
                        enableLights(true)
                        enableVibration(true)
                        setShowBadge(true)
                    }
                    nm.createNotificationChannel(liveChannel)
                }

                // 3. Standard alert channel (Pop up on screen, Default importance)
                if (nm.getNotificationChannel(ALERT_CHANNEL_TOAST_ID) == null) {
                    val toastAlertChannel = NotificationChannel(
                        ALERT_CHANNEL_TOAST_ID,
                        context.getString(R.string.monitor_notif_toast_channel),
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = context.getString(R.string.monitor_notif_channel_desc)
                        enableLights(true)
                        enableVibration(true)
                        setShowBadge(true)
                    }
                    nm.createNotificationChannel(toastAlertChannel)
                }
            }
        }

        fun updateNotification(
            context: Context,
            packageName: String,
            appName: String,
            opName: String,
            count: Int,
            op: Int = AppOpsConstants.OP_READ_CLIPBOARD,
            uid: Int = 0,
            forceLive: Boolean? = null
        ) {
            ensureNotificationChannels(context)
            val nm = NotificationManagerCompat.from(context)

            if (!nm.areNotificationsEnabled()) {
                AppLogger.w("Notifications are disabled by user")
                return
            }

            val notifId = getAlertNotificationId(packageName, op)

            // Inspect action (opens app detail)
            val inspectIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_INSPECT
                putExtra(EXTRA_PACKAGE, packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val inspectPendingIntent = PendingIntent.getActivity(
                context,
                notifId * 10 + 1,
                inspectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val text = if (count > 1) {
                context.getString(R.string.monitor_notif_blocked_multi, appName, opName, count)
            } else {
                context.getString(R.string.monitor_notif_blocked_single, appName, opName)
            }

            // Temporary allow pending intent
            val tempAllowPending = if (AlertPreferences.isTempAllowEnabled.value && op > 0) {
                val duration = AlertPreferences.tempAllowDuration.value
                val tempAllowIntent = Intent(context, TempAllowReceiver::class.java).apply {
                    action = TempAllowReceiver.ACTION_TEMP_ALLOW
                    putExtra(TempAllowReceiver.EXTRA_PACKAGE, packageName)
                    putExtra(TempAllowReceiver.EXTRA_UID, uid)
                    putExtra(TempAllowReceiver.EXTRA_OP, op)
                    putExtra(TempAllowReceiver.EXTRA_DURATION, duration)
                    putExtra(TempAllowReceiver.EXTRA_NOTIFICATION_ID, notifId)
                }
                PendingIntent.getBroadcast(
                    context,
                    notifId * 10 + 2,
                    tempAllowIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else null

            // Dismiss pending intent
            val dismissIntent = Intent(context, TempAllowReceiver::class.java).apply {
                action = TempAllowReceiver.ACTION_DISMISS_NOTIFICATION
                putExtra(TempAllowReceiver.EXTRA_PACKAGE, packageName)
                putExtra(TempAllowReceiver.EXTRA_OP, op)
                putExtra(TempAllowReceiver.EXTRA_NOTIFICATION_ID, notifId)
            }
            val dismissPending = PendingIntent.getBroadcast(
                context,
                notifId * 10 + 3,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val useLive = forceLive ?: AlertPreferences.isLiveUpdatesActive()
            val onlyAlertOnce = count > 1

            if (useLive) {
                // Live Updates Mode: post promoted ongoing notification (status bar pill only)
                postLiveAlertNotification(
                    context = context,
                    notificationId = notifId,
                    text = text,
                    onlyAlertOnce = onlyAlertOnce,
                    inspectPending = inspectPendingIntent,
                    tempAllowPending = tempAllowPending,
                    dismissPending = dismissPending
                )
            } else {
                // Standard notification without "实时更新"
                postStandardAlertNotification(
                    context = context,
                    notificationId = notifId,
                    text = text,
                    onlyAlertOnce = onlyAlertOnce,
                    inspectPending = inspectPendingIntent,
                    tempAllowPending = tempAllowPending,
                    dismissPending = dismissPending
                )
            }
        }

        private fun postStandardAlertNotification(
            context: Context,
            notificationId: Int,
            text: String,
            onlyAlertOnce: Boolean,
            inspectPending: PendingIntent,
            tempAllowPending: PendingIntent?,
            dismissPending: PendingIntent
        ) {
            val nm = NotificationManagerCompat.from(context)
            val builder = NotificationCompat.Builder(context, ALERT_CHANNEL_TOAST_ID)
                .setSmallIcon(R.drawable.ic_notification_shield)
                .setContentTitle(context.getString(R.string.monitor_notif_toast_mode_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setGroup(ALERT_GROUP_KEY)
                .setOngoing(false)
                .setAutoCancel(true)
                .setOnlyAlertOnce(onlyAlertOnce)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(inspectPending)
                .addAction(
                    0,
                    context.getString(R.string.monitor_notif_action_inspect),
                    inspectPending
                )

            if (tempAllowPending != null) {
                val duration = AlertPreferences.tempAllowDuration.value
                builder.addAction(
                    0,
                    context.getString(R.string.monitor_notif_action_temp_allow, duration),
                    tempAllowPending
                )
            }

            builder.addAction(
                0,
                context.getString(R.string.monitor_notif_action_close),
                dismissPending
            )

            try {
                nm.notify(notificationId, builder.build())
            } catch (e: Throwable) {
                AppLogger.w("Failed to post standard alert notification", e)
            }
        }

        private fun postLiveAlertNotification(
            context: Context,
            notificationId: Int,
            text: String,
            onlyAlertOnce: Boolean,
            inspectPending: PendingIntent,
            tempAllowPending: PendingIntent?,
            dismissPending: PendingIntent
        ) {
            val nm = NotificationManagerCompat.from(context)

            if (Build.VERSION.SDK_INT >= 36) {
                try {
                    val progressStyle = Notification.ProgressStyle()
                        .setProgress(100)
                        .addProgressSegment(Notification.ProgressStyle.Segment(100))

                    val shortCritical = context.getString(R.string.status_bar_chip_blocked)

                    val nativeBuilder = Notification.Builder(context, ALERT_CHANNEL_LIVE_ID)
                        .setSmallIcon(R.drawable.ic_notification_shield)
                        .setContentTitle(context.getString(R.string.monitor_notif_live_title))
                        .setSubText(context.getString(R.string.live_update_subtext))
                        .setContentText(text)
                        .setStyle(progressStyle)
                        .setGroup(ALERT_GROUP_KEY)
                        .setOngoing(true)
                        .setShortCriticalText(shortCritical)
                        .setOnlyAlertOnce(onlyAlertOnce)
                        .setContentIntent(inspectPending)
                        .addAction(
                            Notification.Action.Builder(
                                null,
                                context.getString(R.string.monitor_notif_action_inspect),
                                inspectPending
                            ).build()
                        )

                    // setRequestPromotedOngoing was introduced in API 37, invoke safely via reflection if present
                    runCatching {
                        nativeBuilder.javaClass.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                            .invoke(nativeBuilder, true)
                    }

                    if (tempAllowPending != null) {
                        val duration = AlertPreferences.tempAllowDuration.value
                        nativeBuilder.addAction(
                            Notification.Action.Builder(
                                null,
                                context.getString(R.string.monitor_notif_action_temp_allow, duration),
                                tempAllowPending
                            ).build()
                        )
                    }

                    nativeBuilder.addAction(
                        Notification.Action.Builder(
                            null,
                            context.getString(R.string.monitor_notif_action_close),
                            dismissPending
                        ).build()
                    )

                    val notif = nativeBuilder.build()
                    notif.flags = notif.flags or Notification.FLAG_PROMOTED_ONGOING
                    AppLogger.i("postLiveAlertNotification (Android 16): hasPromotableCharacteristics=${notif.hasPromotableCharacteristics()}")
                    nm.notify(notificationId, notif)
                    return
                } catch (e: Throwable) {
                    AppLogger.w("Failed to post live alert notification (Android 16), falling back to compat", e)
                }
            }

            // Pre-Android 16 fallback
            val builder = NotificationCompat.Builder(context, ALERT_CHANNEL_LIVE_ID)
                .setSmallIcon(R.drawable.ic_notification_shield)
                .setContentTitle(context.getString(R.string.monitor_notif_live_title))
                .setSubText(context.getString(R.string.live_update_subtext))
                .setContentText(text)
                .setGroup(ALERT_GROUP_KEY)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(onlyAlertOnce)
                .setOngoing(true)
                .setContentIntent(inspectPending)
                .addAction(0, context.getString(R.string.monitor_notif_action_inspect), inspectPending)

            if (tempAllowPending != null) {
                val duration = AlertPreferences.tempAllowDuration.value
                builder.addAction(
                    0,
                    context.getString(R.string.monitor_notif_action_temp_allow, duration),
                    tempAllowPending
                )
            }

            builder.addAction(
                0,
                context.getString(R.string.monitor_notif_action_close),
                dismissPending
            )

            try {
                nm.notify(notificationId, builder.build())
            } catch (e: Throwable) {
                AppLogger.w("Failed to post fallback live notification", e)
            }
        }

        private fun createCountdownIcon(context: Context, number: Int): Icon {
            val density = context.resources.displayMetrics.density
            val size = (24 * density).toInt().coerceAtLeast(64)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val strokeWidth = size * 0.08f
            val radius = (size / 2f) - (strokeWidth / 2f)
            val cx = size / 2f
            val cy = size / 2f

            // 半透明圆形背景填充 (与 ic_notification_shield 风格一致)
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(51, 255, 255, 255) // #33FFFFFF
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, radius, fillPaint)

            // 纯白圆形边框
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
            }
            canvas.drawCircle(cx, cy, radius, strokePaint)

            // 倒计时数字
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = if (number >= 10) size * 0.50f else size * 0.60f
            }

            val fontMetrics = textPaint.fontMetrics
            val textY = cy - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(number.toString(), cx, textY, textPaint)

            return Icon.createWithBitmap(bitmap)
        }

        fun updateCountdownNotification(
            context: Context,
            packageName: String,
            appName: String,
            opName: String,
            remainingSeconds: Int,
            totalSeconds: Int,
            op: Int,
            uid: Int,
            notificationId: Int = getAlertNotificationId(packageName, op)
        ) {
            ensureNotificationChannels(context)
            val nm = NotificationManagerCompat.from(context)
            if (!nm.areNotificationsEnabled()) return

            val progressPercent = ((remainingSeconds * 100) / totalSeconds).coerceIn(0, 100)
            val text = context.getString(R.string.monitor_notif_temp_countdown, appName, remainingSeconds)

            val dismissIntent = Intent(context, TempAllowReceiver::class.java).apply {
                action = TempAllowReceiver.ACTION_DISMISS_NOTIFICATION
                putExtra(TempAllowReceiver.EXTRA_PACKAGE, packageName)
                putExtra(TempAllowReceiver.EXTRA_OP, op)
                putExtra(TempAllowReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val dismissPending = PendingIntent.getBroadcast(
                context,
                notificationId * 10 + 3,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val inspectIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_INSPECT
                putExtra(EXTRA_PACKAGE, packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val inspectPending = PendingIntent.getActivity(
                context,
                notificationId * 10 + 1,
                inspectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= 36) {
                try {
                    val countdownIcon = createCountdownIcon(context, remainingSeconds)
                    val segment = Notification.ProgressStyle.Segment(100)
                    val progressStyle = Notification.ProgressStyle()
                        .setProgress(progressPercent)
                        .addProgressSegment(segment)

                    val nativeBuilder = Notification.Builder(context, ALERT_CHANNEL_LIVE_ID)
                        .setSmallIcon(countdownIcon)
                        .setContentTitle(context.getString(R.string.monitor_notif_live_title))
                        .setSubText(context.getString(R.string.live_update_subtext))
                        .setContentText(text)
                        .setStyle(progressStyle)
                        .setGroup(ALERT_GROUP_KEY)
                        .setOngoing(true)
                        .setShortCriticalText("${remainingSeconds}s")
                        .setOnlyAlertOnce(true)
                        .setContentIntent(inspectPending)
                        .addAction(
                            Notification.Action.Builder(
                                null,
                                context.getString(R.string.monitor_notif_action_close),
                                dismissPending
                            ).build()
                        )

                    // setRequestPromotedOngoing was introduced in API 37, invoke safely via reflection if present
                    runCatching {
                        nativeBuilder.javaClass.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                            .invoke(nativeBuilder, true)
                    }

                    val notif = nativeBuilder.build()
                    notif.flags = notif.flags or Notification.FLAG_PROMOTED_ONGOING
                    nm.notify(notificationId, notif)
                    return
                } catch (e: Throwable) {
                    AppLogger.w("Failed to notify countdown (Android 16), falling back to compat", e)
                }
            }

            val builder = NotificationCompat.Builder(context, ALERT_CHANNEL_LIVE_ID)
                .setSmallIcon(R.drawable.ic_notification_shield)
                .setContentTitle(context.getString(R.string.monitor_notif_live_title))
                .setContentText(text)
                .setGroup(ALERT_GROUP_KEY)
                .setProgress(100, progressPercent, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(inspectPending)
                .addAction(0, context.getString(R.string.monitor_notif_action_close), dismissPending)

            try {
                nm.notify(notificationId, builder.build())
            } catch (e: Throwable) {
                AppLogger.w("Failed to notify countdown", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannels(this)
        shizukuObserverJob = serviceScope.launch {
            ShizukuManager.status.collect { status ->
                if (_isRunning.value && status == ShizukuState.AUTHORIZED) {
                    startWatching()
                } else if (status == ShizukuState.DEAD || status == ShizukuState.NOT_RUNNING) {
                    AppOpsNotedWatcher.reset()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopWatching()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            _isRunning.value = false
            return START_NOT_STICKY
        }

        startForeground(FOREGROUND_NOTIFICATION_ID, createInitialNotification())
        _isRunning.value = true

        if (ShizukuManager.isAuthorized) {
            startWatching()
        }

        return START_STICKY
    }

    private fun startWatching() {
        try {
            AppOpsNotedWatcher.startWatching(AppOpsConstants.allManagedOps)
        } catch (e: Throwable) {
            AppLogger.e("Failed to startWatching in service", e)
        }
    }

    private fun stopWatching() {
        try {
            AppOpsNotedWatcher.stopWatching()
        } catch (e: Throwable) {
            AppLogger.e("Failed to stopWatching in service", e)
        }
    }

    private fun createInitialNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setContentTitle(getString(R.string.monitor_notif_initial_title))
            .setContentText(getString(R.string.monitor_notif_initial_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        shizukuObserverJob?.cancel()
        serviceScope.cancel()
        stopWatching()
        _isRunning.value = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
