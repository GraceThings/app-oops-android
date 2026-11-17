package io.github.gracethings.appoops

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.gracethings.appoops.data.repository.AppRepository
import io.github.gracethings.appoops.data.shizuku.ShizukuManager
import io.github.gracethings.appoops.service.InterceptionEventManager
import io.github.gracethings.appoops.ui.theme.ThemePreferences
import io.github.gracethings.appoops.util.AppLogger
import org.lsposed.hiddenapibypass.HiddenApiBypass

class AppOopsApplication : Application() {

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val pkg = intent?.data?.schemeSpecificPart
            AppLogger.i("Package changed broadcast: action=${intent?.action}, pkg=$pkg")
            AppRepository.markPackagesDirty(pkg)
        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base?.let { io.github.gracethings.appoops.data.preference.LanguagePreferences.wrapContext(it) })
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
        io.github.gracethings.appoops.data.preference.LanguagePreferences.init(this)
        io.github.gracethings.appoops.data.preference.AlertPreferences.init(this)
        io.github.gracethings.appoops.data.preference.SubscriptionPreferences.init(this)
        io.github.gracethings.appoops.data.repository.AuditRepository.init(this)
        io.github.gracethings.appoops.data.repository.CommunityRuleRepository.loadCachedRules(this)
        InterceptionEventManager.init(this)
        ShizukuManager.init()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageChangeReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }
}
