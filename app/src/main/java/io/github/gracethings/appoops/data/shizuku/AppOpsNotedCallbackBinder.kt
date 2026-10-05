package io.github.gracethings.appoops.data.shizuku

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import io.github.gracethings.appoops.util.AppLogger
import java.lang.reflect.Proxy

class AppOpsNotedCallbackBinder(
    private val onNoted: (op: Int, uid: Int, packageName: String, mode: Int) -> Unit
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
                "opNoted" -> {
                    val op = (args?.getOrNull(0) as? Number)?.toInt() ?: 0
                    val uid = (args?.getOrNull(1) as? Number)?.toInt() ?: 0
                    if (uid < android.os.Process.FIRST_APPLICATION_UID) {
                        return@newProxyInstance null
                    }
                    val pkg = args?.getOrNull(2) as? String ?: ""
                    val mode = (args?.getOrNull(args.size - 1) as? Number)?.toInt() ?: 0
                    AppLogger.d("Proxy opNoted: op=$op, uid=$uid, pkg=$pkg, mode=$mode")
                    onNoted(op, uid, pkg, mode)
                    null
                }
                "toString" -> "AppOpsNotedCallbackProxy"
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

                // Filter out system daemons and native services (UID < 10000)
                if (uid < android.os.Process.FIRST_APPLICATION_UID) {
                    return true
                }

                // Op sanity check
                if (op < 0 || op > 256) {
                    AppLogger.w("Invalid op code received: $op for uid: $uid")
                    return true
                }

                val packageName = data.readString() ?: ""
                val attributionTag = data.readString()

                val remainingInts = data.dataAvail() / 4
                var mode = 0
                if (remainingInts >= 2) {
                    // Skip any middle ints (e.g. virtualDeviceId on Android 14+)
                    for (i in 0 until (remainingInts - 2)) {
                        data.readInt()
                    }
                    val opFlags = data.readInt()
                    mode = data.readInt()
                } else if (remainingInts == 1) {
                    mode = data.readInt()
                }

                AppLogger.d("Binder opNoted: op=$op, uid=$uid, pkg=$packageName, mode=$mode")
                onNoted(op, uid, packageName, mode)
                return true
            } catch (e: Throwable) {
                AppLogger.e("Error in onTransact for opNoted", e)
                return true
            }
        }
        return super.onTransact(code, data, reply, flags)
    }

    companion object {
        const val DESCRIPTOR = "com.android.internal.app.IAppOpsNotedCallback"

        val transactionCode: Int by lazy {
            runCatching {
                val stubClass = Class.forName("com.android.internal.app.IAppOpsNotedCallback\$Stub")
                val field = stubClass.getDeclaredField("TRANSACTION_opNoted")
                field.isAccessible = true
                field.getInt(null)
            }.getOrDefault(IBinder.FIRST_CALL_TRANSACTION + 0)
        }
    }
}

