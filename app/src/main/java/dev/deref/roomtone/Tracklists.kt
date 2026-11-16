package dev.deref.roomtone

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

// Brano successivo previsto: tracklist della release posseduta → master Discogs → album Deezer. Indicativo.
// ponytail: cache solo in memoria, errori → null senza cache; niente retry
object Tracklists {
    private class Item(val position: String?, val title: String)

    private val cache = LruCache<String, List<Item>>(16)

    suspend fun next(t: Track, ref: AlbumRef?): String? = withContext(Dispatchers.IO) {
        try {
            val list = source(t, ref) ?: return@withContext null
            val k = normKey(Track(t.title, ""))
            val i = list.indexOfFirst { normKey(Track(it.title, "")) == k }
            list.getOrNull(i + 1)?.takeIf { i >= 0 }?.let { n -> if (!n.position.isNullOrEmpty()) "${n.position} · ${n.title}" else n.title }
        } catch (_: Exception) { null }
    }

    private suspend fun source(t: Track, ref: AlbumRef?): List<Item>? {
        val own = (if (ref?.source == AlbumSource.OWNED && ref.releaseId != null) DiscogsCollection.byId(ref.releaseId) else null) ?: DiscogsCollection.owned(t)
        if (own != null) return own.tracks.map { Item(it.position, it.title) }
        if (ref == null) return null
        ref.masterId?.let { id ->
            return cached("m:$id") {
                val a = DiscogsApi.get("/masters/$id").optJSONArray("tracklist")
                (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) }.filter { it.optString("type_") == "track" }
                    .map { Item(it.optString("position").takeIf { p -> p.isNotEmpty() }, it.optString("title")) }
            }
        }
        return cached("d:${ref.artist}|${ref.title}") {
            val fk = firstArtist(ref.artist); val ak = normKey(Track(ref.title, ""))
            val q = URLEncoder.encode("${firstArtistRaw(ref.artist)} ${ref.title}", "UTF-8")
            val hits = JSONObject(httpGet("https://api.deezer.com/search/album?q=$q")?.toString(Charsets.UTF_8) ?: return@cached null).optJSONArray("data")
            val al = (0 until (hits?.length() ?: 0)).map { hits!!.getJSONObject(it) }.firstOrNull {
                base(it.optJSONObject("artist")?.optString("name").orEmpty()).contains(fk) && normKey(Track(it.optString("title"), "")) == ak
            } ?: return@cached emptyList()
            val a = JSONObject(httpGet("https://api.deezer.com/album/${al.getLong("id")}/tracks")?.toString(Charsets.UTF_8) ?: return@cached null).optJSONArray("data")
            (0 until (a?.length() ?: 0)).map { Item(null, a!!.getJSONObject(it).optString("title")) }
        }
    }

    private suspend fun cached(key: String, load: suspend () -> List<Item>?): List<Item>? =
        cache.get(key) ?: load()?.also { cache.put(key, it) }
}
