package com.netswitch.widget

import android.content.Context
import android.os.IBinder
import android.telephony.SubscriptionManager
import kotlin.system.exitProcess

/**
 * Работает в отдельном процессе, который Shizuku запускает с правами shell (ADB).
 * У shell есть MODIFY_PHONE_STATE, поэтому отсюда можно менять разрешённые типы сети
 * через скрытый системный интерфейс ITelephony.
 */
class NetService() : INetService.Stub() {

    @Suppress("unused")
    constructor(context: Context) : this()

    override fun destroy() {
        exitProcess(0)
    }

    override fun getAllowedTypes(): Long = try {
        call(telephony(), "getAllowedNetworkTypesForReason", subId(), REASON_USER) as Long
    } catch (t: Throwable) {
        -1L
    }

    override fun setAllowedTypes(mask: Long): String? = try {
        val ok = call(telephony(), "setAllowedNetworkTypesForReason", subId(), REASON_USER, mask)
        if (ok == false) "Система отклонила запрос" else null
    } catch (t: Throwable) {
        val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
        cause.toString()
    }

    private fun telephony(): Any = service("phone", "com.android.internal.telephony.ITelephony")

    private fun subId(): Int {
        runCatching {
            val id = call(service("isub", "com.android.internal.telephony.ISub"), "getDefaultDataSubId") as Int
            if (id >= 0) return id
        }
        val id = SubscriptionManager.getDefaultDataSubscriptionId()
        require(id >= 0) { "Не найдена SIM-карта для мобильных данных" }
        return id
    }

    private fun service(name: String, iface: String): Any {
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, name) as IBinder? ?: error("Сервис $name недоступен")
        return Class.forName("$iface\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)!!
    }

    private fun call(target: Any, name: String, vararg args: Any): Any? {
        val method = target.javaClass.methods.first { it.name == name && it.parameterTypes.size == args.size }
        return method.invoke(target, *args)
    }

    private companion object {
        const val REASON_USER = 0 // TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER
    }
}
