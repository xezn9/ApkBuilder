package com.apkbuilder.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** يراقب حالة الإنترنت ليعرف التطبيق متى يستأنف بعد الانقطاع. */
class NetworkMonitor(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private fun NetworkCapabilities.usable() =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    fun isOnline(): Boolean {
        val n = cm.activeNetwork ?: return false
        return cm.getNetworkCapabilities(n)?.usable() == true
    }

    /** يعود فوراً إن كان الإنترنت متاحاً، وإلا ينتظر عودته (مع مهلة 30 ثانية ليعيد المحاولة دورياً). */
    suspend fun await() {
        if (isOnline()) return
        var callback: ConnectivityManager.NetworkCallback? = null
        try {
            withTimeoutOrNull(30_000) {
                suspendCancellableCoroutine<Unit> { cont ->
                    val cb = object : ConnectivityManager.NetworkCallback() {
                        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                            if (caps.usable() && cont.isActive) cont.resume(Unit)
                        }
                    }
                    callback = cb
                    cm.registerDefaultNetworkCallback(cb)
                }
            }
        } finally {
            callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        }
    }
}
