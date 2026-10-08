package io.github.gracethings.appoops.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import io.github.gracethings.appoops.service.AppOpsMonitorService
import io.github.gracethings.appoops.service.TempAllowManager
import io.github.gracethings.appoops.util.AppLogger

class TempAllowReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TEMP_ALLOW = "io.github.gracethings.appoops.ACTION_TEMP_ALLOW"
        const val ACTION_DISMISS_NOTIFICATION = "io.github.gracethings.appoops.ACTION_DISMISS_NOTIFICATION"

        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_UID = "extra_uid"
        const val EXTRA_OP = "extra_op"
        const val EXTRA_DURATION = "extra_duration"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        AppLogger.i("TempAllowReceiver onReceive: action=$action")

        when (action) {
            ACTION_TEMP_ALLOW -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: return
                val uid = intent.getIntExtra(EXTRA_UID, 0)
                val op = intent.getIntExtra(EXTRA_OP, 0)
                val duration = intent.getIntExtra(EXTRA_DURATION, 10)
                val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, AppOpsMonitorService.ALERT_NOTIFICATION_ID)

                TempAllowManager.temporaryAllow(context, packageName, uid, op, duration)
                if (!io.github.gracethings.appoops.data.preference.AlertPreferences.isLiveUpdatesActive()) {
                    NotificationManagerCompat.from(context).cancel(notifId)
                }
            }
            ACTION_DISMISS_NOTIFICATION -> {
                val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, AppOpsMonitorService.ALERT_NOTIFICATION_ID)
                TempAllowManager.cancelAndRevertAll(context)
                NotificationManagerCompat.from(context).cancel(notifId)
            }
        }
    }
}

