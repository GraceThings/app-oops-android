package io.github.gracethings.appoops.data.shizuku

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import io.github.gracethings.appoops.util.AppLogger
import java.lang.reflect.Proxy

class AppOpsStartedCallbackBinder(
    private val onStarted: (op: Int, uid: Int, packageName: String, mode: Int) -> Unit
) : Binder(), IInterface {

    init {
        attachInterface(this, DESCRIPTOR)
    }

    override fun asBinder(): IBinder = this

    override fun getInterfaceDescriptor(): String = DESCRIPTOR

    val proxy: Any by lazy {
        val callbackClass = Class.forName(DESCRIPTOR)
        Proxy.newProxyInstance(
            callbackClass.classLoader,
            arrayOf(callbackClass)
        ) { _, method, args ->
            when (method.name) {
                "asBinder" -> this
                "opStarted" -> {
                    val op = (args?.getOrNull(0) as? Number)?.toInt() ?: 0
                    val uid = (args?.getOrNull(1) as? Number)?.toInt() ?: 0
                    if (uid < android.os.Process.FIRST_APPLICATION_UID) {
                        return@newProxyInstance null
                    }
                    val pkg = args?.getOrNull(2) as? String ?: ""
                    val mode = extractModeFromArgs(args)
                    AppLogger.d("Proxy opStarted: op=$op, uid=$uid, pkg=$pkg, mode=$mode")
                    onStarted(op, uid, pkg, mode)
                    null
                }
                "toString" -> "AppOpsStartedCallbackProxy"
                "hashCode" -> hashCode()
                "equals" -> args?.getOrNull(0) === this
                else -> null
            }
        }
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == transactionCode) {
            try {
                data.enforceInterface(DESCRIPTOR)
                val op = data.readInt()
                val uid = data.readInt()

                if (uid < android.os.Process.FIRST_APPLICATION_UID) {
                    return true
                }

                if (op < 0 || op > 256) {
                    AppLogger.w("Invalid op code received in opStarted: $op for uid: $uid")
                    return true
                }

                val packageName = data.readString() ?: ""
                @Suppress("UNUSED_VARIABLE")
                val attributionTag = data.readString()

                val trailingCount = data.dataAvail() / 4
                val trailingInts = IntArray(trailingCount) { data.readInt() }
                val mode = extractModeFromTrailingInts(trailingInts)

                AppLogger.d("Binder opStarted: op=$op, uid=$uid, pkg=$packageName, mode=$mode")
                onStarted(op, uid, packageName, mode)
                return true
            } catch (e: Throwable) {
                AppLogger.e("Error in onTransact for opStarted", e)
                return true
            }
        }
        return super.onTransact(code, data, reply, flags)
    }

    companion object {
        const val DESCRIPTOR = "com.android.internal.app.IAppOpsStartedCallback"

        val transactionCode: Int by lazy {
            runCatching {
                val stubClass = Class.forName("com.android.internal.app.IAppOpsStartedCallback\$Stub")
                val field = stubClass.getDeclaredField("TRANSACTION_opStarted")
                field.isAccessible = true
                field.getInt(null)
            }.getOrDefault(IBinder.FIRST_CALL_TRANSACTION)
        }

        // AOSP IAppOpsStartedCallback.opStarted trailing ints:
        // API 31-33: [flags, mode, startedType, attributionFlags, attributionChainId] -> mode at size - 4
        // API 34+:   [virtualDeviceId, flags, mode, startedType, attributionFlags, attributionChainId] -> mode at size - 4
        private fun extractModeFromTrailingInts(ints: IntArray): Int {
            return when {
                ints.size >= 5 -> ints[ints.size - 4]
                ints.isNotEmpty() -> ints.last()
                else -> 0
            }
        }

        private fun extractModeFromArgs(args: Array<Any?>?): Int {
            if (args == null) return 0
            val trailing = args.drop(4).mapNotNull { (it as? Number)?.toInt() }.toIntArray()
            return extractModeFromTrailingInts(trailing)
        }
    }
}
