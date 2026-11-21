package dev.deref.roomtone

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// Nome con " & " = gruppo o più artisti? Cache (memoria + file) → Discogs → MusicBrainz (solo se Discogs in errore).
// Discogs ha i duo/gruppi (North & South) ma non le collaborazioni (Artist One & Artist Two); iTunes sì → scartato.
// "sì" per sempre, "no" per 30 giorni.
// ponytail: errore di rete → non in cache e trattato come non gruppo; niente retry
object Artists {
    private const val TTL = 30 * 24 * 3_600_000L

    private var file: File? = null
    private val cache = HashMap<String, LongArray>() // chiave → [1 = gruppo / 0 = no, timestamp]
    private val cacheLock = Mutex()

    fun init(dir: File?) { // idempotente
        if (file != null || dir == null) return
        file = File(dir, "artist_groups_v2.json") // v2: prima solo MusicBrainz
        try {
            val o = JSONObject(file!!.readText())
            o.keys().forEach { k -> o.getJSONArray(k).let { cache[k] = longArrayOf(it.getLong(0), it.getLong(1)) } }
        } catch (_: Exception) { /* assente o corrotta → cache vuota */ }
    }

    suspend fun resolve(names: List<String>): Set<String> = coroutineScope {
        val r = names.map { n ->
            async {
                val key = base(n).trim()
                val hit = cacheLock.withLock { cache[key] }
                when {
                    hit != null && (hit[0] == 1L || System.currentTimeMillis() - hit[1] < TTL) -> hit[0] == 1L
                    else -> lookup(n, key)
                }
            }
        }.awaitAll()
        persist()
        names.filterIndexedTo(HashSet()) { i, _ -> r[i] }
    }

    private suspend fun lookup(n: String, key: String): Boolean {
        if (BuildConfig.DISCOGS_CONSUMER_KEY.isNotEmpty()) try {
            val r = DiscogsApi.get("/database/search", mapOf("type" to "artist", "q" to n, "per_page" to "10")).optJSONArray("results")
            val g = (0 until (r?.length() ?: 0)).any { base(cleanDiscogsArtist(r!!.getJSONObject(it).optString("title"))).trim() == key }
            store(key, g)
            return g
        } catch (_: Exception) { /* rete/HTTP → MusicBrainz */ }
        val a = MusicBrainz.search("artist", "artist:\"${MusicBrainz.esc(n)}\"")?.optJSONArray("artists") ?: return false
        val g = (0 until a.length()).map { a.getJSONObject(it) }.any { r ->
            r.optInt("score") >= 95 && (base(r.optString("name")).trim() == key ||
                r.optJSONArray("aliases")?.let { al -> (0 until al.length()).any { base(al.getJSONObject(it).optString("name")).trim() == key } } == true)
        }
        store(key, g)
        return g
    }

    private var dirty = false

    private suspend fun store(key: String, g: Boolean) = cacheLock.withLock {
        cache[key] = longArrayOf(if (g) 1 else 0, System.currentTimeMillis())
        dirty = true
    }

    // Una scrittura per batch, solo se cambiato
    private suspend fun persist() = cacheLock.withLock {
        if (!dirty) return@withLock
        dirty = false
        try {
            val o = JSONObject()
            cache.forEach { (k, v) -> o.put(k, JSONArray().put(v[0]).put(v[1])) }
            file?.writeText(o.toString())
        } catch (_: Exception) { /* ponytail: cache solo in memoria se la scrittura fallisce */ }
    }
}
