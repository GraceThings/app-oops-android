package io.github.gracethings.appoops

import android.app.Application
import android.content.Context
import android.os.Build
import io.github.gracethings.appoops.data.shizuku.ShizukuManager
import io.github.gracethings.appoops.service.InterceptionEventManager
import io.github.gracethings.appoops.ui.theme.ThemePreferences
import io.github.gracethings.appoops.util.AppLogger
import org.lsposed.hiddenapibypass.HiddenApiBypass

class AppOopsApplication : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("L")
            } catch (e: Throwable) {
                AppLogger.w("HiddenApiBypass failed: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppLogger.i("AppOopsApplication onCreate")

        ThemePreferences.init(this)
        io.github.gracethings.appoops.data.preference.AlertPreferences.init(this)
        io.github.gracethings.appoops.data.preference.SubscriptionPreferences.init(this)
        io.github.gracethings.appoops.data.repository.CommunityRuleRepository.loadCachedRules(this)
        InterceptionEventManager.init(this)
        ShizukuManager.init()
    }
}

