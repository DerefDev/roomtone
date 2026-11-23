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
    private val _screenOffAuto = MutableStateFlow(false) // in standby lascia spegnere lo schermo dopo N minuti
    val screenOffAuto: StateFlow<Boolean> = _screenOffAuto
    private val _screenOffMin = MutableStateFlow(10)
    val screenOffMin: StateFlow<Int> = _screenOffMin

    private val _autoStartShazam = MutableStateFlow(true)
    val autoStartShazam: StateFlow<Boolean> = _autoStartShazam
    private val _restartShazamOnScreenOn = MutableStateFlow(true)
    val restartShazamOnScreenOn: StateFlow<Boolean> = _restartShazamOnScreenOn

    private val _albumMode = MutableStateFlow("history") // "history" = coerente con la cronologia, "owned" = disco reale
    val albumMode: StateFlow<String> = _albumMode

    private val _diagnostics = MutableStateFlow(false) // nascosta: 7 tocchi sulla versione
    val diagnostics: StateFlow<Boolean> = _diagnostics

    private val _logDays = MutableStateFlow(2) // i log si svuotano ogni N giorni
    val logDays: StateFlow<Int> = _logDays

    private val _editOptions = MutableStateFlow(EditOptions())
    val editOptions: StateFlow<EditOptions> = _editOptions

    @Synchronized fun init(ctx: Context) { // idempotente
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _scrobbling.value = sp.getBoolean("scrobbling", true)
        _standbyAuto.value = sp.getBoolean("standby_auto", true)
        _standbyMin.value = sp.getInt("standby_min", 10)
        _screenOffAuto.value = sp.getBoolean("screen_off_auto", false)
        _screenOffMin.value = sp.getInt("screen_off_min", 10)
        _autoStartShazam.value = sp.getBoolean("autoStartShazam", true)
        _restartShazamOnScreenOn.value = sp.getBoolean("restartShazamOnScreenOn", true)
        _diagnostics.value = sp.getBoolean("diagnostics", false)
        _logDays.value = sp.getInt("log_days", 2)
        _albumMode.value = sp.getString("albumMode", "history")?.takeIf { it == "owned" } ?: "history"
        val d = EditOptions()
        fun b(n: String, def: Boolean) = sp.getBoolean("edit_$n", def)
        _editOptions.value = EditOptions(b("remaster", d.remaster), b("versions", d.versions), b("live", d.live), b("explicit", d.explicit),
            b("introOutro", d.introOutro), b("featToArtist", d.featToArtist), b("albumEditions", d.albumEditions), b("mainArtistOnly", d.mainArtistOnly))
    }

    fun setEditOptions(o: EditOptions) {
        _editOptions.value = o
        sp.edit().putBoolean("edit_remaster", o.remaster).putBoolean("edit_versions", o.versions).putBoolean("edit_live", o.live)
            .putBoolean("edit_explicit", o.explicit).putBoolean("edit_introOutro", o.introOutro).putBoolean("edit_featToArtist", o.featToArtist)
            .putBoolean("edit_albumEditions", o.albumEditions).putBoolean("edit_mainArtistOnly", o.mainArtistOnly).apply()
    }

    fun setScrobbling(v: Boolean) { _scrobbling.value = v; sp.edit().putBoolean("scrobbling", v).apply() }
    fun setStandbyAuto(v: Boolean) { _standbyAuto.value = v; sp.edit().putBoolean("standby_auto", v).apply() }
    fun setAutoStartShazam(v: Boolean) { _autoStartShazam.value = v; sp.edit().putBoolean("autoStartShazam", v).apply() }
    fun setRestartShazamOnScreenOn(v: Boolean) { _restartShazamOnScreenOn.value = v; sp.edit().putBoolean("restartShazamOnScreenOn", v).apply() }
    fun setLogDays(v: Int) { _logDays.value = v; sp.edit().putInt("log_days", v).apply() }
    // inizio del periodo di log corrente (0 = mai)
    var logStartedAt: Long
        get() = sp.getLong("log_started_at", 0)
        set(v) { sp.edit().putLong("log_started_at", v).apply() }
    fun setDiagnostics(v: Boolean) { _diagnostics.value = v; sp.edit().putBoolean("diagnostics", v).apply() }
    fun setAlbumMode(v: String) { _albumMode.value = v; sp.edit().putString("albumMode", v).apply() }
    fun setStandbyMin(v: Int) { _standbyMin.value = v; sp.edit().putInt("standby_min", v).apply() }
    fun setScreenOffAuto(v: Boolean) { _screenOffAuto.value = v; sp.edit().putBoolean("screen_off_auto", v).apply() }
    fun setScreenOffMin(v: Int) { _screenOffMin.value = v; sp.edit().putInt("screen_off_min", v).apply() }
}
