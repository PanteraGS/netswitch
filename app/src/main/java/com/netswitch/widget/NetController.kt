package com.netswitch.widget

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

class NetException(message: String) : Exception(message)

object NetController {

    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val PREFS = "net"
    private const val KEY_MODE = "mode"

    private var service: INetService? = null
    private val mutex = Mutex()

    fun shizukuState(context: Context): ShizukuState {
        if (!Shizuku.pingBinder()) {
            val installed = try {
                context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0); true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
            return if (installed) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED
        }
        if (Shizuku.isPreV11()) return ShizukuState.NOT_RUNNING
        return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            ShizukuState.READY
        } else {
            ShizukuState.NO_PERMISSION
        }
    }

    /** Режим, показанный последним — чтобы виджет сразу рисовался без обращения к системе. */
    fun cachedMode(context: Context): NetMode? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, null)
            ?.let { name -> NetMode.entries.firstOrNull { it.name == name } }

    private fun saveMode(context: Context, mode: NetMode?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, mode?.name).apply()
    }

    /** Читает реальный режим из системы и обновляет все виджеты и плитку. */
    suspend fun refresh(context: Context): NetMode? {
        val mode = runCatching {
            val mask = withContext(Dispatchers.IO) { service(context).allowedTypes }
            NetMode.fromMask(mask)
        }.getOrNull() ?: return cachedMode(context)
        saveMode(context, mode)
        NetWidgetProvider.updateAll(context)
        NetTileService.requestUpdate(context)
        return mode
    }

    /** Переключает сеть. Бросает NetException с понятным текстом при ошибке. */
    suspend fun switchTo(context: Context, mode: NetMode): NetMode {
        val error = withContext(Dispatchers.IO) { service(context).setAllowedTypes(mode.mask) }
        if (error != null) {
            throw NetException(
                when (error) {
                    NetService.ERR_REJECTED -> context.getString(R.string.error_rejected)
                    NetService.ERR_NO_SIM -> context.getString(R.string.error_no_sim)
                    NetService.ERR_NO_SERVICE -> context.getString(R.string.error_no_service)
                    else -> error
                },
            )
        }
        saveMode(context, mode)
        return refresh(context) ?: mode
    }

    private suspend fun service(context: Context): INetService = mutex.withLock {
        service?.takeIf { it.asBinder().pingBinder() }?.let { return it }

        withTimeoutOrNull(3000) { awaitBinder() }
        when (shizukuState(context)) {
            ShizukuState.NOT_INSTALLED -> throw NetException(context.getString(R.string.error_shizuku_not_installed))
            ShizukuState.NOT_RUNNING -> throw NetException(context.getString(R.string.error_shizuku_not_running))
            ShizukuState.NO_PERMISSION -> throw NetException(context.getString(R.string.error_shizuku_no_permission))
            ShizukuState.READY -> Unit
        }

        val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, NetService::class.java.name))
            .daemon(false)
            .processNameSuffix("net")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

        withTimeout(10_000) {
            suspendCancellableCoroutine { cont ->
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        if (binder == null || !binder.pingBinder()) return
                        val s = INetService.Stub.asInterface(binder)
                        service = s
                        if (cont.isActive) cont.resume(s)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        service = null
                    }
                }
                Shizuku.bindUserService(args, connection)
            }
        }
    }

    private suspend fun awaitBinder() {
        if (Shizuku.pingBinder()) return
        suspendCancellableCoroutine { cont ->
            val listener = object : Shizuku.OnBinderReceivedListener {
                override fun onBinderReceived() {
                    Shizuku.removeBinderReceivedListener(this)
                    if (cont.isActive) cont.resume(Unit)
                }
            }
            Shizuku.addBinderReceivedListenerSticky(listener)
            cont.invokeOnCancellation { Shizuku.removeBinderReceivedListener(listener) }
        }
    }
}
