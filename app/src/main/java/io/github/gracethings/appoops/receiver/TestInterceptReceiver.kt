package io.github.gracethings.appoops.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.gracethings.appoops.data.model.OpMode
import io.github.gracethings.appoops.service.InterceptionEventManager
import io.github.gracethings.appoops.util.AppLogger
import io.github.gracethings.appoops.util.AppOpsConstants

class TestInterceptReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TEST_INTERCEPT) {
            val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: "com.taobao.taobao"
            val op = intent.getIntExtra(EXTRA_OP, AppOpsConstants.OP_READ_CLIPBOARD)
            val uid = intent.getIntExtra(EXTRA_UID, 10001)
            AppLogger.i("TestInterceptReceiver triggered: pkg=$pkg, op=$op, uid=$uid")
            InterceptionEventManager.onIntercepted(op, uid, pkg, OpMode.IGNORE)
        }
    }

    companion object {
        const val ACTION_TEST_INTERCEPT = "io.github.gracethings.appoops.TEST_INTERCEPT"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_OP = "op"
        const val EXTRA_UID = "uid"
    }
}

