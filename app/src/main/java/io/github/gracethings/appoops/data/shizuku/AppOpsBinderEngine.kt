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
    private var getPackagesForOpsMethod: Method? = null
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
        getPackagesForOpsMethod = sClass.methods.firstOrNull { it.name == "getPackagesForOps" && it.parameterTypes.size == 1 }
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
     * Batch queries all packages with overridden ops for targetOps in a single IPC call.
     * Returns map of packageName -> (opCode -> mode), or null if binder call fails.
     */
    fun getOverriddenOpsForPackages(targetOps: IntArray): Map<String, Map<Int, Int>>? {
        val s = ensureService()
        val m = getPackagesForOpsMethod ?: return null
        return runCatching {
            val list = m.invoke(s, targetOps) as? List<*> ?: return null
            val resultMap = mutableMapOf<String, MutableMap<Int, Int>>()
            for (pkgOp in list) {
                if (pkgOp == null) continue
                val getPackageName = pkgOp.javaClass.methods.firstOrNull { it.name == "getPackageName" }
                val packageName = getPackageName?.invoke(pkgOp) as? String ?: continue
                val getOps = pkgOp.javaClass.methods.firstOrNull { it.name == "getOps" } ?: continue
                val entries = getOps.invoke(pkgOp) as? List<*> ?: continue
                val appMap = resultMap.getOrPut(packageName) { mutableMapOf() }
                for (entry in entries) {
                    if (entry == null) continue
                    val getOp = entry.javaClass.methods.firstOrNull { it.name == "getOp" }
                    val getMode = entry.javaClass.methods.firstOrNull { it.name == "getMode" }
                    val op = (getOp?.invoke(entry) as? Number)?.toInt()
                    val mode = (getMode?.invoke(entry) as? Number)?.toInt()
                    if (op != null && mode != null) {
                        appMap[op] = mode
                    }
                }
            }
            resultMap
        }.getOrNull()
    }

    /**
     * Queries the actual configured AppOp mode without being polluted by runtime permission checks.
     * Returns null if unable to determine via Binder.
     */
    fun getOpMode(code: Int, uid: Int, packageName: String): Int? {
        val s = ensureService()

        // Strategy 1: getOpsForPackage (queries package-level explicit overrides only).
        // DO NOT query getUidOps, because Android OS automatically sets Uid mode: ignore
        // for any ungranted runtime permissions (e.g. BODY_SENSORS) across all apps!
        getOpsForPackageMethod?.let { m ->
            val res = runCatching {
                m.invoke(s, uid, packageName, intArrayOf(code)) as? List<*>
            }.getOrNull()

            if (res != null) {
                val mode = extractModeFromPackageOps(res, code)
                // If package ops has an explicit override, return it.
                // If not found in package ops, the package has no override -> MODE_DEFAULT!
                return mode ?: io.github.gracethings.appoops.util.AppOpsConstants.MODE_DEFAULT
            }
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

