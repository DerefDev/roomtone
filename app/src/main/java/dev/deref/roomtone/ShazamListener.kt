package dev.deref.roomtone

import android.app.Notification
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

class ShazamListener : NotificationListenerService() {

    private val io = Executors.newSingleThreadExecutor()

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            EventStore.tick(System.currentTimeMillis())
            Updates.checkIfStale(applicationContext) // controllo giornaliero anche con app in background
            DiscogsCollection.startIfStale() // collezione ogni 6 h anche con app sempre aperta
            handler.postDelayed(this, 15_000)
        }
    }

    // privacy: schermo spento → Auto Shazam spento; riacceso → riavvio (se attivo da pref) dall'activity visibile
    private var screenRx: BroadcastReceiver? = null
    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> ShazamControl.onScreenOff()
                Intent.ACTION_USER_PRESENT -> ShazamControl.onScreenOn(applicationContext)
                // SCREEN_ON con blocco schermo attivo: si aspetta USER_PRESENT
                Intent.ACTION_SCREEN_ON -> if (!(getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked) ShazamControl.onScreenOn(applicationContext)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Updates.init(this)
        Connectivity.init(this)
        Durations.init(getExternalFilesDir(null))
        Services.init(this)
        DiscogsCollection.init(this)
        EventStore.init(filesDir)
        Albums.init(cacheDir)
        Artists.init(cacheDir)
        EventStore.onEvaluated = { l, e, m, s -> saveListen(l, e, m, "listen", s) }
        EventStore.onEdited = { l, e, m, s -> saveListen(l, e, m, "edit", s) } // riga di modifica: "start" identifica l'ascolto // agganciato subito, non solo a listener connesso
    }

    override fun onListenerConnected() {
        EventStore.setConnected(true)
        try { ShazamControl.recompute(activeNotifications) } catch (_: SecurityException) { }
        ShazamControl.listenerUp = true
        if (screenRx == null) {
            screenRx = rx
            ContextCompat.registerReceiver(this, rx, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        ShazamControl.maybeAutoStart(applicationContext)
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 15_000)
    }

    override fun onListenerDisconnected() {
        EventStore.setConnected(false)
        ShazamControl.listenerUp = false
        unregisterScreenRx()
        handler.removeCallbacks(ticker)
    }

    // Log su file (thread io): svuotati ogni Prefs.logDays giorni
    private fun log(name: String, line: String) {
        val dir = getExternalFilesDir(null) ?: return
        val now = System.currentTimeMillis()
        val start = Prefs.logStartedAt
        if (start == 0L || now - start > Prefs.logDays.value * 86_400_000L) {
            File(dir, "shazam_raw.jsonl").delete(); File(dir, "listens.jsonl").delete()
            Prefs.logStartedAt = now
        }
        File(dir, name).appendText(line + "\n")
    }

    private fun saveListen(l: Listen, e: Evaluation, m: ScrobbleMeta, event: String, s: Service) {
        val line = JSONObject().apply {
            put("event", event); put("service", s.name)
            put("title", l.track.title); put("artist", l.track.artist)
            put("start", l.start); put("firstBeat", l.firstBeat); put("lastBeat", l.lastBeat)
            put("beats", l.beats); put("playedMs", l.playedMs)
            put("durationMs", e.durationMs ?: JSONObject.NULL)
            put("status", e.status.name); put("reason", e.reason.name); put("plays", e.plays)
            put("timestamps", JSONArray(e.timestamps))
            put("scrobbleArtist", m.artist); put("scrobbleTitle", m.title)
            put("scrobbleAlbum", m.album ?: JSONObject.NULL); put("metaSource", m.source.name)
            put("albumSource", m.albumSource?.name ?: JSONObject.NULL)
        }.toString()
        io.execute {
            try {
                log("listens.jsonl", line)
            } catch (_: Exception) { /* ponytail: errori di scrittura ignorati → log su logcat */ }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn, "posted")
    override fun onNotificationRemoved(sbn: StatusBarNotification) = handle(sbn, "removed")

    private fun unregisterScreenRx() {
        if (screenRx == null) return
        screenRx = null
        try { unregisterReceiver(rx) } catch (_: IllegalArgumentException) { }
    }

    override fun onDestroy() {
        // gli hook puntano a questa istanza: dopo shutdown di io andrebbero in errore
        EventStore.onEvaluated = { _, _, _, _ -> }
        EventStore.onEdited = { _, _, _, _ -> }
        unregisterScreenRx()
        handler.removeCallbacks(ticker)
        io.shutdown()
        super.onDestroy()
    }

    private fun handle(sbn: StatusBarNotification, event: String) {
        if (sbn.packageName != "com.shazam.android") return
        if (event == "posted") ShazamControl.onPosted(sbn) else ShazamControl.onRemoved(sbn)
        val n = sbn.notification
        val now = System.currentTimeMillis()
        val line = toJson(sbn, n, event, now).toString()
        // scrittura su thread dedicato, append + chiusura (flush) ad ogni riga
        io.execute {
            try {
                log("shazam_raw.jsonl", line)
            } catch (_: Exception) { /* ponytail: errori di scrittura ignorati → log su logcat */ }
        }
        if (event != "posted") return
        val channel = if (Build.VERSION.SDK_INT >= 26) n.channelId else null
        when {
            channel == "auto_shazam_v2" && sbn.isOngoing ->
                n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.let { EventStore.beat(it, now) }
            channel == "notification_shazam_match_v1" && sbn.tag == "NOTIFICATION_SHAZAM_RESULTS" -> {
                val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                val artist = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                if (!title.isNullOrBlank() && !artist.isNullOrBlank()) EventStore.match(title, artist, now, largeIcon(n))
            }
        }
    }

    // Icona grande della notifica (copertina Shazam); null se assente/illeggibile
    private fun largeIcon(n: Notification) = try { n.getLargeIcon()?.loadDrawable(this)?.toBitmap() } catch (_: Exception) { null }

    private fun toJson(sbn: StatusBarNotification, n: Notification, event: String, now: Long) = JSONObject().apply {
        put("event", event)
        put("wallTime", now)
        put("postTime", sbn.postTime)
        put("key", sbn.key)
        put("id", sbn.id)
        put("tag", sbn.tag ?: JSONObject.NULL)
        put("channelId", if (Build.VERSION.SDK_INT >= 26) n.channelId ?: JSONObject.NULL else JSONObject.NULL)
        put("isOngoing", sbn.isOngoing)
        put("category", n.category ?: JSONObject.NULL)
        put("extras", JSONObject().also { o ->
            n.extras.keySet().forEach { k ->
                o.put(k, when (val v = n.extras.get(k)) {
                    null -> JSONObject.NULL
                    is CharSequence -> v.toString()
                    is Number, is Boolean -> v
                    else -> v.toString()
                })
            }
        })
        put("actions", JSONArray().also { a -> n.actions?.forEach { a.put(it.title?.toString()) } })
    }
}
