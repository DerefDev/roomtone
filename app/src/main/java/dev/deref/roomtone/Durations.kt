package dev.deref.roomtone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder

// Durata brano: disco posseduto (Discogs) → cache (memoria + durations.json) → Last.fm → Deezer → MusicBrainz (ultima: 1 req/s).
// ponytail: errori di rete → null senza dettaglio; niente retry → WorkManager se serve
object Durations {
    private const val DAY = 24 * 3_600_000L

    private var file: File? = null
    private val cache = HashMap<String, LongArray>() // chiave → [ms (-1 = non trovato), timestamp]
    private val cacheLock = Mutex()

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
        DiscogsCollection.ownedTrack(t)?.second?.durationMs?.takeIf { it > 0 }?.let { return@withContext it }
        cacheLock.withLock { cache[key] }?.let { (ms, ts) ->
            if (ms > 0) return@withContext ms
            if (System.currentTimeMillis() - ts < DAY) return@withContext null // non trovato di recente
        }
        var failed = false
        val sources = listOf<suspend (Track) -> Long?>(
            { if (BuildConfig.LASTFM_API_KEY.isEmpty()) 0L else lastFm(it) }, { deezer(it) }, { musicBrainz(it) },
        )
        for (src in sources) {
            val ms = src(t)
            if (ms == null) failed = true
            else if (ms > 0) { store(key, ms); return@withContext ms }
        }
        if (failed) return@withContext null // errore/503 → niente cache
        store(key, -1)
        null
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

    // 0 = sconosciuta, null = errore
    private fun lastFm(t: Track): Long? {
        val u = "https://ws.audioscrobbler.com/2.0/?method=track.getInfo&api_key=${enc(BuildConfig.LASTFM_API_KEY)}" +
            "&artist=${enc(t.artist)}&track=${enc(t.title)}&autocorrect=1&format=json"
        val body = try { httpGet(u)?.toString(Charsets.UTF_8) } catch (_: IOException) { null } ?: return null
        return try {
            JSONObject(body).optJSONObject("track")?.optString("duration")?.toLongOrNull() ?: 0L
        } catch (_: Exception) { null }
    }

    // 0 = non trovata, null = errore
    private fun deezer(t: Track): Long? {
        val u = "https://api.deezer.com/search?q=" + enc("${firstArtistRaw(t.artist)} ${stripFeat(t.title)}")
        val body = try { httpGet(u)?.toString(Charsets.UTF_8) } catch (_: IOException) { null } ?: return null
        return try {
            val a = JSONObject(body).optJSONArray("data") ?: return 0L
            val fk = firstArtist(t.artist); val tk = titleKey(t.title)
            val ok = (0 until a.length()).map { a.getJSONObject(it) }.filter {
                base(it.optJSONObject("artist")?.optString("name").orEmpty()).contains(fk) && titleKey(it.optString("title")) == tk && it.optLong("duration") > 0
            }
            ((ok.firstOrNull { it.optJSONObject("album")?.optString("type") == "album" } ?: ok.firstOrNull())?.optLong("duration") ?: 0L) * 1000
        } catch (_: Exception) { null }
    }

    // Shazam scrive "(feat. X)" nel titolo e più artisti uniti con "&": MusicBrainz spesso non li trova così.
    // Ripiego: titolo senza feat. e solo il primo artista.
    private suspend fun musicBrainz(t: Track): Long? {
        val first = mbQuery(t.title, t.artist) ?: return null
        if (first > 0) return first
        val t2 = stripFeat(t.title)
        val a2 = firstArtistRaw(t.artist)
        if (t2 == t.title && a2 == t.artist) return 0L
        return mbQuery(t2, a2)
    }

    // 0 = non trovata, null = errore
    private suspend fun mbQuery(title: String, artist: String): Long? {
        val o = MusicBrainz.search("recording", "recording:\"${MusicBrainz.esc(title)}\" AND artist:\"${MusicBrainz.esc(artist)}\"") ?: return null
        val a = o.optJSONArray("recordings") ?: return null
        for (i in 0 until a.length()) {
            val r = a.getJSONObject(i)
            if (r.optInt("score") >= 90 && !r.isNull("length") && r.optLong("length") > 0) return r.getLong("length")
        }
        return 0L
    }
}
