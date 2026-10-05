package io.github.gracethings.appoops.data.shizuku

import android.os.IBinder
import io.github.gracethings.appoops.util.AppLogger
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

object AppOpsBinderEngine {
    private var serviceInstance: Any? = null
    private var setModeMethod: Method? = null
    private var checkOpMethod: Method? = null
    private var startWatchingNotedMethod: Method? = null
    private var stopWatchingNotedMethod: Method? = null

    @Synchronized
    private fun ensureService(): Any {
        serviceInstance?.let { return it }

        val rawBinder: IBinder = SystemServiceHelper.getSystemService("appops")
            ?: throw IllegalStateException("AppOps system service unavailable")
        val wrappedBinder = ShizukuBinderWrapper(rawBinder)

        val stubClass = Class.forName("com.android.internal.app.IAppOpsService\$Stub")
        val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
        val s = asInterfaceMethod.invoke(null, wrappedBinder)
            ?: throw IllegalStateException("Failed to get IAppOpsService instance")

        serviceInstance = s
        val sClass = s.javaClass

        // Cache methods via reflection
        setModeMethod = sClass.methods.firstOrNull { it.name == "setMode" && it.parameterTypes.size >= 4 }
        checkOpMethod = sClass.methods.firstOrNull { it.name == "checkOperation" && it.parameterTypes.size >= 3 }
        startWatchingNotedMethod = sClass.methods.firstOrNull { it.name == "startWatchingNoted" }
        stopWatchingNotedMethod = sClass.methods.firstOrNull { it.name == "stopWatchingNoted" }

        return s
    }

    fun setMode(code: Int, uid: Int, packageName: String, mode: Int) {
        val s = ensureService()
        val m = setModeMethod ?: throw NoSuchMethodException("setMode method not found")
        m.invoke(s, code, uid, packageName, mode)
    }

    fun checkOperation(code: Int, uid: Int, packageName: String): Int {
        val s = ensureService()
        val m = checkOpMethod ?: throw NoSuchMethodException("checkOperation method not found")
        val res = m.invoke(s, code, uid, packageName)
        return (res as? Number)?.toInt() ?: 3
    }

    fun startWatchingNoted(ops: IntArray, callbackBinder: AppOpsNotedCallbackBinder): Boolean {
        return try {
            val s = ensureService()
            val m = startWatchingNotedMethod ?: return false
            m.invoke(s, ops, callbackBinder.proxy)
            true
        } catch (e: Throwable) {
            AppLogger.e("Failed to invoke startWatchingNoted", e)
            false
        }
    }

    fun stopWatchingNoted(callbackBinder: AppOpsNotedCallbackBinder): Boolean {
        return try {
            val s = ensureService()
            val m = stopWatchingNotedMethod ?: return false
            m.invoke(s, callbackBinder.proxy)
            true
        } catch (e: Throwable) {
            AppLogger.w("Failed to invoke stopWatchingNoted", e)
            false
        }
    }
}

