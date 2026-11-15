package dev.deref.roomtone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Durata brano: cache (memoria + durations.json) → Last.fm → MusicBrainz.
// ponytail: errori di rete → null senza dettaglio; niente retry → WorkManager se serve
object Durations {
    private const val DAY = 24 * 3_600_000L
    private const val UA = "Roomtone/0.3 ( https://github.com/DerefDev/roomtone )"

    private var file: File? = null
    private val cache = HashMap<String, LongArray>() // chiave → [ms (-1 = non trovato), timestamp]
    private val cacheLock = Mutex()
    private val mbLock = Mutex()
    private var lastMb = 0L

    fun init(dir: File?) { // idempotente
        if (file != null || dir == null) return
        file = File(dir, "durations.json")
        try {
            val o = JSONObject(file!!.readText())
            o.keys().forEach { k -> o.getJSONArray(k).let { cache[k] = longArrayOf(it.getLong(0), it.getLong(1)) } }
        } catch (_: Exception) { /* assente o corrotta → cache vuota */ }
    }

    suspend fun duration(t: Track): Long? = withContext(Dispatchers.IO) {
        val key = normKey(t)
        cacheLock.withLock { cache[key] }?.let { (ms, ts) ->
            if (ms > 0) return@withContext ms
            if (System.currentTimeMillis() - ts < DAY) return@withContext null // non trovato di recente
        }
        var ms = 0L
        if (BuildConfig.LASTFM_API_KEY.isNotEmpty()) ms = lastFm(t) ?: 0L // errore → prova MusicBrainz
        if (ms <= 0) ms = musicBrainz(t) ?: return@withContext null // errore/503 → niente cache
        store(key, if (ms > 0) ms else -1)
        ms.takeIf { it > 0 }
    }

    private suspend fun store(key: String, ms: Long) = cacheLock.withLock {
        cache[key] = longArrayOf(ms, System.currentTimeMillis())
        try {
            val o = JSONObject()
            cache.forEach { (k, v) -> o.put(k, JSONArray().put(v[0]).put(v[1])) }
            file?.writeText(o.toString())
        } catch (_: Exception) { /* ponytail: cache solo in memoria se la scrittura fallisce */ }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): String? = try {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 10_000; readTimeout = 10_000
            setRequestProperty("User-Agent", UA)
            try { if (responseCode == 200) inputStream.bufferedReader().readText() else null } finally { disconnect() }
        }
    } catch (_: Exception) { null }

    // 0 = sconosciuta, null = errore
    private fun lastFm(t: Track): Long? {
        val u = "https://ws.audioscrobbler.com/2.0/?method=track.getInfo&api_key=${enc(BuildConfig.LASTFM_API_KEY)}" +
            "&artist=${enc(t.artist)}&track=${enc(t.title)}&autocorrect=1&format=json"
        val body = get(u) ?: return null
        return try {
            JSONObject(body).optJSONObject("track")?.optString("duration")?.toLongOrNull() ?: 0L
        } catch (_: Exception) { null }
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private val featTitle = Regex("\\s*[(\\[][^)\\]]*\\b(?:feat|ft|featuring)\\b[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE)
    private val otherArtists = Regex("\\s*(?:&|,|\\bfeat\\b|\\bft\\.|\\bfeaturing\\b).*$", RegexOption.IGNORE_CASE)

    // Shazam scrive "(feat. X)" nel titolo e più artisti uniti con "&": MusicBrainz spesso non li trova così.
    // Ripiego: titolo senza feat. e solo il primo artista.
    private suspend fun musicBrainz(t: Track): Long? {
        val first = mbQuery(t.title, t.artist) ?: return null
        if (first > 0) return first
        val t2 = featTitle.replace(t.title, "").trim()
        val a2 = otherArtists.replace(t.artist, "").trim()
        if (t2 == t.title && a2 == t.artist) return 0L
        return mbQuery(t2, a2)
    }

    // 0 = non trovata, null = errore
    private suspend fun mbQuery(title: String, artist: String): Long? = mbLock.withLock {
        // 1 richiesta/secondo
        val wait = lastMb + 1_100 - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        val q = "recording:\"${esc(title)}\" AND artist:\"${esc(artist)}\""
        val body = get("https://musicbrainz.org/ws/2/recording?query=${enc(q)}&fmt=json&limit=5")
        lastMb = System.currentTimeMillis()
        if (body == null) return@withLock null
        try {
            val a = JSONObject(body).getJSONArray("recordings")
            for (i in 0 until a.length()) {
                val r = a.getJSONObject(i)
                if (r.optInt("score") >= 90 && !r.isNull("length") && r.optLong("length") > 0) return@withLock r.getLong("length")
            }
            0L
        } catch (_: Exception) { null }
    }
}
