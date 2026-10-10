package io.github.gracethings.appoops.data.shizuku

import android.os.IBinder
import io.github.gracethings.appoops.util.AppLogger
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

object AppOpsBinderEngine {
    private var serviceInstance: Any? = null
    private var setModeMethod: Method? = null
    private var setUidModeMethod: Method? = null
    private var getOpsForPackageMethod: Method? = null
    private var getUidOpsMethod: Method? = null
    private var unsafeCheckOpRawMethod: Method? = null
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
        setUidModeMethod = sClass.methods.firstOrNull { it.name == "setUidMode" && it.parameterTypes.size >= 3 }
        getOpsForPackageMethod = sClass.methods.firstOrNull { it.name == "getOpsForPackage" && it.parameterTypes.size == 3 }
        getUidOpsMethod = sClass.methods.firstOrNull { it.name == "getUidOps" && it.parameterTypes.size == 2 }
        unsafeCheckOpRawMethod = sClass.methods.firstOrNull { it.name == "unsafeCheckOpRaw" && it.parameterTypes.size >= 3 }
            ?: sClass.methods.firstOrNull { it.name == "checkOperationRaw" && it.parameterTypes.size >= 3 }
        checkOpMethod = sClass.methods.firstOrNull { it.name == "checkOperation" && it.parameterTypes.size >= 3 }
        startWatchingNotedMethod = sClass.methods.firstOrNull { it.name == "startWatchingNoted" }
        stopWatchingNotedMethod = sClass.methods.firstOrNull { it.name == "stopWatchingNoted" }

        return s
    }

    fun setMode(code: Int, uid: Int, packageName: String, mode: Int) {
        val s = ensureService()
        val m = setModeMethod ?: throw NoSuchMethodException("setMode method not found")
        m.invoke(s, code, uid, packageName, mode)
        // Also update UID-level mode for UID-scoped operations if available
        setUidModeMethod?.let { uidMethod ->
            runCatching { uidMethod.invoke(s, code, uid, mode) }
        }
    }

    /**
     * Queries the actual configured AppOp mode without being polluted by runtime permission checks.
     * Returns null if unable to determine via Binder.
     */
    fun getOpMode(code: Int, uid: Int, packageName: String): Int? {
        val s = ensureService()

        // Strategy 1: getOpsForPackage & getUidOps (the same method used by 'cmd appops get')
        // If neither returns an entry for this opCode, it has never been modified -> MODE_DEFAULT
        var hasQueriedOps = false
        var packageMode: Int? = null
        var uidMode: Int? = null

        getOpsForPackageMethod?.let { m ->
            runCatching {
                val res = m.invoke(s, uid, packageName, intArrayOf(code)) as? List<*>
                hasQueriedOps = true
                packageMode = extractModeFromPackageOps(res, code)
            }
        }

        getUidOpsMethod?.let { m ->
            runCatching {
                val res = m.invoke(s, uid, intArrayOf(code)) as? List<*>
                hasQueriedOps = true
                uidMode = extractModeFromPackageOps(res, code)
            }
        }

        if (hasQueriedOps) {
            if (packageMode != null && packageMode != io.github.gracethings.appoops.util.AppOpsConstants.MODE_DEFAULT) {
                return packageMode
            }
            if (uidMode != null && uidMode != io.github.gracethings.appoops.util.AppOpsConstants.MODE_DEFAULT) {
                return uidMode
            }
            if (packageMode != null) return packageMode
            if (uidMode != null) return uidMode
            // Both queried and found no entry for this op -> it is in system default state
            return io.github.gracethings.appoops.util.AppOpsConstants.MODE_DEFAULT
        }

        // Strategy 2: unsafeCheckOpRaw (raw mode without permission checks)
        unsafeCheckOpRawMethod?.let { m ->
            val res = runCatching {
                val r = m.invoke(s, code, uid, packageName)
                (r as? Number)?.toInt()
            }.getOrNull()
            if (res != null) return res
        }

        return null
    }

    private fun extractModeFromPackageOps(list: List<*>?, targetOp: Int): Int? {
        if (list.isNullOrEmpty()) return null
        for (pkgOp in list) {
            if (pkgOp == null) continue
            val getOps = pkgOp.javaClass.methods.firstOrNull { it.name == "getOps" } ?: continue
            val entries = getOps.invoke(pkgOp) as? List<*> ?: continue
            for (entry in entries) {
                if (entry == null) continue
                val getOp = entry.javaClass.methods.firstOrNull { it.name == "getOp" }
                val getMode = entry.javaClass.methods.firstOrNull { it.name == "getMode" }
                val op = (getOp?.invoke(entry) as? Number)?.toInt()
                val mode = (getMode?.invoke(entry) as? Number)?.toInt()
                if (op == targetOp && mode != null) {
                    return mode
                }
            }
        }
        return null
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

