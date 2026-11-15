package dev.deref.roomtone

import android.app.Notification
import android.os.Build
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
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Durations.init(getExternalFilesDir(null))
        HistorySync.init(this)
        EventStore.onEvaluated = ::saveListen // agganciato subito, non solo a listener connesso
    }

    override fun onListenerConnected() {
        EventStore.setConnected(true)
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 15_000)
    }

    override fun onListenerDisconnected() {
        EventStore.setConnected(false)
        handler.removeCallbacks(ticker)
    }

    private fun saveListen(l: Listen, e: Evaluation, m: ScrobbleMeta) {
        val line = JSONObject().apply {
            put("title", l.track.title); put("artist", l.track.artist)
            put("start", l.start); put("firstBeat", l.firstBeat); put("lastBeat", l.lastBeat)
            put("beats", l.beats); put("playedMs", l.playedMs)
            put("durationMs", e.durationMs ?: JSONObject.NULL)
            put("status", e.status.name); put("reason", e.reason.name); put("plays", e.plays)
            put("timestamps", JSONArray(e.timestamps))
            put("scrobbleArtist", m.artist); put("scrobbleTitle", m.title)
            put("scrobbleAlbum", m.album ?: JSONObject.NULL); put("metaSource", m.source.name)
        }.toString()
        io.execute {
            try {
                getExternalFilesDir(null)?.let { File(it, "listens.jsonl").appendText(line + "\n") }
            } catch (_: Exception) { /* ponytail: errori di scrittura ignorati → log su logcat */ }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn, "posted")
    override fun onNotificationRemoved(sbn: StatusBarNotification) = handle(sbn, "removed")

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        io.shutdown()
        super.onDestroy()
    }

    private fun handle(sbn: StatusBarNotification, event: String) {
        if (sbn.packageName != "com.shazam.android") return
        val n = sbn.notification
        val now = System.currentTimeMillis()
        val line = toJson(sbn, n, event, now).toString()
        // scrittura su thread dedicato, append + chiusura (flush) ad ogni riga
        io.execute {
            try {
                getExternalFilesDir(null)?.let { File(it, "shazam_raw.jsonl").appendText(line + "\n") }
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
                if (!title.isNullOrBlank() && !artist.isNullOrBlank()) EventStore.match(title, artist, now)
            }
        }
    }

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
                    is CharSequence, is Number, is Boolean -> if (v is CharSequence) v.toString() else v
                    else -> v.toString()
                })
            }
        })
        put("actions", JSONArray().also { a -> n.actions?.forEach { a.put(it.title?.toString()) } })
    }
}
