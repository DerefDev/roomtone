package dev.deref.roomtone

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

// Copertina provvisoria da iTunes Search API (nessuna key). Cache: memoria (8) + disco; errori → null.
// ponytail: provvisorio → sostituito da Discogs
object Artwork {
    private const val UA = "Roomtone/0.5 ( https://github.com/DerefDev/roomtone )"
    private const val SIDE = 1024 // lato massimo di decodifica
    private var dir: File? = null
    private val mem = LruCache<String, Bitmap>(8)
    private val missing = HashSet<String>() // "non trovato" per la sessione
    private val firstSep = Regex("&|,|;|\\band\\b|\\bfeat\\b|\\bft\\b|\\bfeaturing\\b")

    fun init(cacheDir: File) { dir = File(cacheDir, "artwork").apply { mkdirs() } }

    suspend fun load(artist: String, title: String, album: String?): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val first = base(firstSep.split(artist.lowercase()).first()).trim()
            if (first.isEmpty()) return@withContext null
            val key = base(if (album != null) "$first|$album" else "$first|$title")
            mem.get(key)?.let { return@withContext it }
            synchronized(missing) { if (key in missing) return@withContext null }
            val f = File(dir ?: return@withContext null, hash(key) + ".jpg")
            if (!f.exists()) {
                val url = findUrl(first, title, album)
                if (url == null) { synchronized(missing) { missing += key }; return@withContext null }
                val bytes = fetch(url) ?: return@withContext null
                f.writeBytes(bytes)
            }
            decode(f)?.also { mem.put(key, it) } ?: run { f.delete(); null } // file corrotto → riscarica la prossima volta
        } catch (_: Exception) { null }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // null = errore o nessun risultato con artista coerente
    private fun findUrl(first: String, title: String, album: String?): String? {
        val q = if (album != null) "term=${enc("$first $album")}&entity=album" else "term=${enc("$first $title")}&entity=song"
        val body = fetch("https://itunes.apple.com/search?$q&limit=5&media=music")?.toString(Charsets.UTF_8) ?: return null
        val arr = JSONObject(body).optJSONArray("results") ?: return null
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            if (base(r.optString("artistName")).contains(first)) {
                val u = r.optString("artworkUrl100")
                if (u.isNotEmpty()) return u.replace("100x100bb", "1200x1200bb")
            }
        }
        return null
    }

    private fun fetch(url: String): ByteArray? = try {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 10_000; readTimeout = 10_000
            setRequestProperty("User-Agent", UA)
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
