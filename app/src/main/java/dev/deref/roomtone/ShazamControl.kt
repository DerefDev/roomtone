package dev.deref.roomtone

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

// Controllo di Auto Shazam: stato dalla notifica ongoing, spegnimento via azione, accensione via deep link.
object ShazamControl {
    private const val PKG = "com.shazam.android"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _autoOn = MutableStateFlow(false)
    val autoOn: StateFlow<Boolean> = _autoOn
    private val _notInstalled = MutableStateFlow(false)
    val notInstalled: StateFlow<Boolean> = _notInstalled

    private var notif: StatusBarNotification? = null
    @Volatile var visible = false // MainActivity tra onResume e onPause
    @Volatile var listenerUp = false
    @Volatile var wasOnBeforeScreenOff = false
    @Volatile private var pendingRestart = false
    private var autoStartPending = true // una volta per processo
    private var starting = false
    private var lastStartAt = 0L

    fun isAuto(sbn: StatusBarNotification) = sbn.packageName == PKG && sbn.isOngoing &&
        Build.VERSION.SDK_INT >= 26 && sbn.notification.channelId == "auto_shazam_v2"

    fun onPosted(sbn: StatusBarNotification) { if (isAuto(sbn)) { notif = sbn; _autoOn.value = true } }
    fun onRemoved(sbn: StatusBarNotification) { if (isAuto(sbn)) { notif = null; _autoOn.value = false } }
    fun recompute(active: Array<StatusBarNotification>) {
        notif = active.firstOrNull(::isAuto)
        _autoOn.value = notif != null
    }

    // Con "Mostra sopra altre app" Android consente l'avvio di activity anche da background.
    fun canStartFromBackground(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun stop() {
        Log.d("Roomtone", "Auto Shazam: stop (attivo=${_autoOn.value})")
        // ponytail: indice 0 = "Spegni", verificato su Shazam 16.45 → cercare per titolo/icona se cambia
        try { notif?.notification?.actions?.getOrNull(0)?.actionIntent?.send() } catch (_: PendingIntent.CanceledException) { }
    }

    // Da chiamare solo con MainActivity visibile (Android 10: da background non si avviano activity).
    fun start(ctx: Context) {
        if (_autoOn.value || starting) return
        // Senza listener lo stato di Shazam è ignoto: avviarlo "alla cieca" crea un ciclo di riaperture.
        if (!listenerUp) { Log.d("Roomtone", "Auto Shazam: start saltato, listener scollegato"); return }
        // Al massimo un tentativo al minuto, per sicurezza.
        val now = System.currentTimeMillis()
        if (now - lastStartAt < 60_000) { Log.d("Roomtone", "Auto Shazam: start saltato, troppo ravvicinato"); return }
        lastStartAt = now
        val app = ctx.applicationContext
        Log.d("Roomtone", "Auto Shazam: start (visibile=$visible, overlay=${canStartFromBackground(app)})")
        try {
            app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("shazam://startautotagging")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            _notInstalled.value = false
        } catch (_: ActivityNotFoundException) { _notInstalled.value = true; return }
        starting = true
        scope.launch {
            try {
                withTimeoutOrNull(6_000) { _autoOn.first { it } }
                delay(500)
                // ponytail: se il ritorno fallisce (finestra di grazia scaduta) resta Shazam davanti
                app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            } catch (_: Exception) { } finally { starting = false }
        }
    }

    // Avvio automatico con Roomtone: una volta per processo, a listener connesso e activity visibile.
    fun maybeAutoStart(ctx: Context) {
        if (!autoStartPending || !visible || !listenerUp) return
        autoStartPending = false
        if (Prefs.autoStartShazam.value && !_autoOn.value) start(ctx)
    }

    // Riavvio dopo schermo riacceso: serve activity visibile.
    fun onScreenOff() {
        Log.d("Roomtone", "Schermo spento: attivo=${_autoOn.value}")
        if (_autoOn.value) { wasOnBeforeScreenOff = true; stop() }
    }
    fun onScreenOn(ctx: Context) {
        if (!wasOnBeforeScreenOff) return
        wasOnBeforeScreenOff = false
        Log.d("Roomtone", "Schermo riacceso: riavvio Auto Shazam")
        if (!Prefs.restartShazamOnScreenOn.value) return
        if (canStartFromBackground(ctx)) start(ctx) else { pendingRestart = true; consumeRestart(ctx) }
    }
    fun consumeRestart(ctx: Context) {
        if (!pendingRestart || !visible) return
        pendingRestart = false
        start(ctx)
    }
}
