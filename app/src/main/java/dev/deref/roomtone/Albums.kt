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

enum class AlbumSource { OWNED, HISTORY, DISCOGS, LASTFM, DEEZER }

data class AlbumRef(
    val artist: String, val title: String, val source: AlbumSource,
    val releaseId: Long? = null, val masterId: Long? = null, val discogsImage: String? = null,
)

// Risultato di database/search (master o release)
data class DiscogsHit(val masterId: Long?, val title: String, val year: Int, val formats: List<String>, val image: String?)


// Scelta del master: 1) il suo titolo coincide con l'album di Last.fm; poi per formato:
// Album non Compilation → altri non Compilation (EP) → Single → Compilation; a parità il più vecchio.
fun pickMaster(results: List<DiscogsHit>, lastFmAlbum: String?): DiscogsHit? {
    fun age(h: DiscogsHit) = h.year.takeIf { it > 0 } ?: Int.MAX_VALUE
    if (lastFmAlbum != null) {
        val k = titleKey(lastFmAlbum)
        results.filter { titleKey(it.title) == k }.minByOrNull(::age)?.let { return it }
    }
    fun tier(h: DiscogsHit) = when {
        "Compilation" in h.formats -> 3
        "Album" in h.formats -> 0
        "Single" in h.formats -> 2
        else -> 1
    }
    return results.minWithOrNull(compareBy(::tier, ::age))
}

// Risoluzione album: collezione Discogs → cronologia → catalogo Discogs → Last.fm → Deezer.
// Cache per normKey (memoria + albums.json); solo i risultati di rete. Il "no" resta 1 giorno; errori di rete → niente cache.
// ponytail: owned/history non in cache (sono locali e dipendono dalla cronologia corrente)
object Albums {
    private const val DAY = 24 * 3_600_000L
    private var file: File? = null
    private val cache = HashMap<String, JSONObject>() // chiave → {a,t,s,r,m,i,ts} oppure {ts} (non trovato)
    private val cacheLock = Mutex()

    fun init(dir: File?) { // idempotente
        if (file != null || dir == null) return
        file = File(dir, "albums.json")
        try {
            val o = JSONObject(file!!.readText())
            o.keys().forEach { k -> cache[k] = o.getJSONObject(k) }
        } catch (_: Exception) { /* assente o corrotta → cache vuota */ }
    }

    suspend fun resolve(t: Track, historyAlbum: String?): AlbumRef? = withContext(Dispatchers.IO) {
        val first = firstArtistRaw(t.artist)
        DiscogsCollection.owned(t)?.let { return@withContext AlbumRef(it.artist, it.title, AlbumSource.OWNED, it.id, it.masterId, it.coverImage) }
        if (historyAlbum != null) return@withContext AlbumRef(first, historyAlbum, AlbumSource.HISTORY)
        val key = normKey(t)
        cacheLock.withLock { cache[key] }?.let { o ->
            if (o.has("t")) return@withContext AlbumRef(o.getString("a"), o.getString("t"), AlbumSource.valueOf(o.getString("s")),
                o.optLong("r").takeIf { it > 0 }, o.optLong("m").takeIf { it > 0 }, o.optString("i").takeIf { it.isNotEmpty() })
            if (System.currentTimeMillis() - o.getLong("ts") < DAY) return@withContext null
        }
        var err = false
        val title = stripFeat(t.title)
        var lfm: LastFmAlbum? = null
        var lfmDone = false
        suspend fun lastFm(): LastFmAlbum? {
            if (!lfmDone && BuildConfig.LASTFM_API_KEY.isNotEmpty()) {
                lfmDone = true
                try { lfm = LastFmApi.trackAlbum(first, title) } catch (_: IOException) { err = true }
            }
            return lfm
        }
        var ref: AlbumRef? = null
        if (BuildConfig.DISCOGS_CONSUMER_KEY.isNotEmpty()) try {
            var hits = search("master", first, title)
            if (hits.isEmpty()) hits = search("release", first, title)
            pickMaster(hits, if (hits.size > 1) lastFm()?.title else null)?.let { ref = AlbumRef(first, it.title, AlbumSource.DISCOGS, null, it.masterId, it.image) }
        } catch (_: IOException) { err = true } catch (_: Exception) { /* JSON inatteso → prossima fonte */ }
        if (ref == null) lastFm()?.let { ref = AlbumRef(first, it.title, AlbumSource.LASTFM) }
        if (ref == null) try { ref = deezer(first, title) } catch (_: IOException) { err = true } catch (_: Exception) { }
        store(key, ref, err)
        ref
    }

    private suspend fun store(key: String, r: AlbumRef?, err: Boolean) {
        if (err) return // qualche fonte in errore → riprova al prossimo ascolto
        cacheLock.withLock {
            cache[key] = if (r == null) JSONObject().put("ts", System.currentTimeMillis())
            else JSONObject().put("a", r.artist).put("t", r.title).put("s", r.source.name).put("ts", System.currentTimeMillis())
                .put("r", r.releaseId ?: 0).put("m", r.masterId ?: 0).put("i", r.discogsImage ?: "")
            try {
                val o = JSONObject()
                cache.forEach { (k, v) -> o.put(k, v) }
                file?.writeText(o.toString())
            } catch (_: Exception) { /* ponytail: cache solo in memoria se la scrittura fallisce */ }
        }
    }

    // IOException = rete/HTTP
    private suspend fun search(type: String, artist: String, track: String): List<DiscogsHit> {
        val a = DiscogsApi.get("/database/search", mapOf("type" to type, "artist" to artist, "track" to track, "per_page" to "25")).optJSONArray("results") ?: return emptyList()
        val fk = firstArtist(artist)
        // "Artista - Titolo": la ricerca per artista è approssimata → tieni solo i risultati con l'artista coerente
        return (0 until a.length()).map { a.getJSONObject(it) }.filter { base(it.optString("title").substringBefore(" - ")).contains(fk) }.map { r ->
            val f = r.optJSONArray("format")
            val id = if (type == "master") r.optLong("id") else r.optLong("master_id")
            DiscogsHit(id.takeIf { it > 0 }, discogsTitle(r.optString("title")), r.optString("year").toIntOrNull() ?: 0,
                f?.let { x -> (0 until x.length()).map(x::getString) } ?: emptyList(), r.optString("cover_image").takeIf { it.isNotEmpty() })
        }
    }

    // Ricerca semplice (la sintassi artist:"" track:"" non funziona su Deezer); preferisce album.type == "album"
    private fun deezer(first: String, title: String): AlbumRef? {
        val body = httpGet("https://api.deezer.com/search?q=${URLEncoder.encode("$first $title", "UTF-8")}")?.toString(Charsets.UTF_8) ?: return null
        val a = JSONObject(body).optJSONArray("data") ?: return null
        val fk = firstArtist(first)
        val tk = titleKey(title)
        val ok = (0 until a.length()).map { a.getJSONObject(it) }.filter {
            base(it.optJSONObject("artist")?.optString("name").orEmpty()).contains(fk) && titleKey(it.optString("title")) == tk &&
                it.optJSONObject("album")?.optString("title").orEmpty().isNotEmpty()
        }
        val r = (ok.firstOrNull { it.getJSONObject("album").optString("type") == "album" } ?: ok.firstOrNull()) ?: return null
        return AlbumRef(first, r.getJSONObject("album").getString("title"), AlbumSource.DEEZER)
    }
}
