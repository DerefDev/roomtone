package dev.deref.vinylkiosk

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

class ShazamListener : NotificationListenerService() {

    private val io = Executors.newSingleThreadExecutor()

    private val matchChannels = setOf("notification_shazam_match_v1", "notification_shazam_foreground_match_v2")

    override fun onListenerConnected() = EventStore.setConnected(true)
    override fun onListenerDisconnected() = EventStore.setConnected(false)

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn, "posted")
    override fun onNotificationRemoved(sbn: StatusBarNotification) = handle(sbn, "removed")

    override fun onDestroy() {
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
        if (channel !in matchChannels || n.actions == null) return
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val artist = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        if (title.isNullOrBlank() || artist.isNullOrBlank()) return
        EventStore.add(title, artist, sbn.key, now)
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
