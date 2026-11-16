package dev.deref.roomtone

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

// Copertina (sez. 7.6): Cover Art Archive del release group (ricerca MusicBrainz) → iTunes. Cache: memoria (8) + disco.
// ponytail: ricerca testuale finché manca la collezione Discogs (v0.7) → poi ID release Discogs → MBID → CAA della release esatta
object Artwork {
    private const val SIDE = 1024 // lato massimo di decodifica
    private var dir: File? = null
    private val mem = LruCache<String, Bitmap>(8)
    private val missing = HashSet<String>() // "non trovato" per la sessione
    private val featTitle = Regex("\\s*[(\\[][^)\\]]*\\b(?:feat|ft|featuring)\\b[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE)
    private val firstSep = Regex("&|,|;|\\band\\b|\\bfeat\\b|\\bft\\b|\\bfeaturing\\b")

    fun init(cacheDir: File) {
        File(cacheDir, "artwork").deleteRecursively() // cache solo iTunes della v0.5 → riscarica da Cover Art Archive
        dir = File(cacheDir, "artwork2").apply { mkdirs() }
    }

    suspend fun load(artist: String, title: String, album: String?): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val first = base(firstSep.split(artist.lowercase()).first()).trim()
            if (first.isEmpty()) return@withContext null
            val key = base(if (album != null) "$first|$album" else "$first|$title")
            mem.get(key)?.let { return@withContext it }
            synchronized(missing) { if (key in missing) return@withContext null }
            val f = File(dir ?: return@withContext null, hash(key) + ".jpg")
            if (!f.exists()) {
                var mbError = false
                val bytes = try { caa(first, title, album) } catch (_: IOException) { mbError = true; null }
                    ?: itunes(first, title, album)
                if (bytes == null) {
                    if (!mbError) synchronized(missing) { missing += key } // MusicBrainz in errore → riprova al prossimo brano
                    return@withContext null
                }
                f.writeBytes(bytes)
            }
            decode(f)?.also { mem.put(key, it) } ?: run { f.delete(); null } // file corrotto → riscarica la prossima volta
        } catch (_: Exception) { null }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun artistOk(o: JSONObject, first: String): Boolean {
        val ac = o.optJSONArray("artist-credit") ?: return false
        return (0 until ac.length()).any { base(ac.getJSONObject(it).optString("name")).contains(first) }
    }

    // Release group dall'album, altrimenti dalle release che contengono il brano (album in studio prima).
    // null = nessuna copertina; IOException = MusicBrainz in errore
    private suspend fun caa(first: String, title: String, album: String?): ByteArray? {
        val ids = LinkedHashSet<String>()
        if (album != null) {
            val o = MusicBrainz.search("release-group", "releasegroup:\"${MusicBrainz.esc(album)}\" AND artist:\"${MusicBrainz.esc(first)}\"")
                ?: throw IOException()
            val a = o.optJSONArray("release-groups")
            for (i in 0 until (a?.length() ?: 0)) {
                val g = a!!.getJSONObject(i)
                if (g.optInt("score") >= 90 && artistOk(g, first)) ids += g.getString("id")
            }
        }
        if (ids.isEmpty()) {
            val t = featTitle.replace(title, "").trim()
            val o = MusicBrainz.search("recording", "recording:\"${MusicBrainz.esc(t)}\" AND artist:\"${MusicBrainz.esc(first)}\"")
                ?: throw IOException()
            val studio = ArrayList<String>(); val other = ArrayList<String>()
            val a = o.optJSONArray("recordings")
            for (i in 0 until (a?.length() ?: 0)) {
                val r = a!!.getJSONObject(i)
                if (r.optInt("score") < 90 || !artistOk(r, first)) continue
                val rels = r.optJSONArray("releases") ?: continue
                for (j in 0 until rels.length()) {
                    val g = rels.getJSONObject(j).optJSONObject("release-group") ?: continue
                    val id = g.optString("id")
                    if (id.isEmpty()) continue
                    val isStudio = g.optString("primary-type") == "Album" && (g.optJSONArray("secondary-types")?.length() ?: 0) == 0
                    (if (isStudio) studio else other) += id
                }
            }
            ids += studio; ids += other
        }
        // CAA: 404 = release group senza copertina → prossimo candidato
        for (id in ids.take(3)) fetch("https://coverartarchive.org/release-group/$id/front-1200")?.let { return it }
        return null
    }

    // null = errore o nessun risultato con artista coerente
    private fun itunes(first: String, title: String, album: String?): ByteArray? {
        val q = if (album != null) "term=${enc("$first $album")}&entity=album" else "term=${enc("$first $title")}&entity=song"
        val body = fetch("https://itunes.apple.com/search?$q&limit=5&media=music")?.toString(Charsets.UTF_8) ?: return null
        val arr = JSONObject(body).optJSONArray("results") ?: return null
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            if (base(r.optString("artistName")).contains(first)) {
                val u = r.optString("artworkUrl100")
                if (u.isNotEmpty()) return fetch(u.replace("100x100bb", "1200x1200bb"))
            }
        }
        return null
    }

    private fun fetch(url: String): ByteArray? = try {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 10_000; readTimeout = 10_000
            setRequestProperty("User-Agent", MusicBrainz.UA)
            try { if (responseCode == 200) inputStream.use { it.readBytes() } else null } finally { disconnect() }
        }
    } catch (_: Exception) { null }

    private fun decode(f: File): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= SIDE) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    private fun hash(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
