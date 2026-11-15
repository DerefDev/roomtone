package dev.deref.roomtone

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Impostazioni utente persistenti (SharedPreferences) esposte come StateFlow.
object Prefs {
    private lateinit var sp: SharedPreferences
    private val _scrobbling = MutableStateFlow(true)
    val scrobbling: StateFlow<Boolean> = _scrobbling
    private val _standbyAuto = MutableStateFlow(true)
    val standbyAuto: StateFlow<Boolean> = _standbyAuto
    private val _standbyMin = MutableStateFlow(10)
    val standbyMin: StateFlow<Int> = _standbyMin

    @Synchronized fun init(ctx: Context) { // idempotente
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _scrobbling.value = sp.getBoolean("scrobbling", true)
        _standbyAuto.value = sp.getBoolean("standby_auto", true)
        _standbyMin.value = sp.getInt("standby_min", 10)
    }

    fun setScrobbling(v: Boolean) { _scrobbling.value = v; sp.edit().putBoolean("scrobbling", v).apply() }
    fun setStandbyAuto(v: Boolean) { _standbyAuto.value = v; sp.edit().putBoolean("standby_auto", v).apply() }
    fun setStandbyMin(v: Int) { _standbyMin.value = v; sp.edit().putInt("standby_min", v).apply() }
}
