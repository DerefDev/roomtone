package dev.deref.roomtone

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Stato online/offline della rete predefinita.
object Connectivity {
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online
    private var registered = false

    @Synchronized fun init(ctx: Context) { // idempotente
        if (registered) return
        val cm = ctx.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return
        registered = true
        _online.value = cm.activeNetwork != null
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { _online.value = true }
            override fun onLost(network: Network) { _online.value = false }
        })
    }
}
