package dev.deref.roomtone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Ricerca MusicBrainz condivisa (durate, copertine): 1 richiesta/secondo per tutta l'app, User-Agent identificativo.
object MusicBrainz {
    const val UA = "Roomtone/0.6 ( https://github.com/DerefDev/roomtone )"
    private val lock = Mutex()
    private var last = 0L

    fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    // null = errore (rete, 503, risposta illeggibile); 404 → oggetto vuoto (risorsa assente, non errore)
    suspend fun get(pathAndQuery: String): JSONObject? = lock.withLock {
        val wait = last + 1_100 - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        val url = "https://musicbrainz.org/ws/2/$pathAndQuery"
        val body = withContext(Dispatchers.IO) {
            try {
                (URL(url).openConnection() as HttpURLConnection).run {
                    connectTimeout = 10_000; readTimeout = 10_000
                    setRequestProperty("User-Agent", UA)
                    try {
                        when (responseCode) {
                            200 -> inputStream.bufferedReader().readText()
                            404 -> "{}"
                            else -> null
                        }
                    } finally { disconnect() }
                }
            } catch (_: Exception) { null }
        }
        last = System.currentTimeMillis()
        try { body?.let(::JSONObject) } catch (_: Exception) { null }
    }

    suspend fun search(entity: String, query: String): JSONObject? =
        get("$entity?query=${URLEncoder.encode(query, "UTF-8")}&fmt=json&limit=5")
}
