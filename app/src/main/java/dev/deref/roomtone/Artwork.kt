package dev.deref.roomtone

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest

data class Candidate(val source: String, val url: String)

private typealias Source = Pair<String, suspend () -> List<String>>

// Copertine. Dischi posseduti (file persistenti in filesDir/covers, override utente vince):
// CAA release esatta → Last.fm → CAA release group → Deezer → immagine Discogs.
// Altri brani (cache in cacheDir/artwork3): Last.fm → CAA release group → Deezer → iTunes → immagine Discogs del master.
object Artwork {
    private const val SIDE = 1024 // lato massimo di decodifica
    private const val THUMB = 300
    private const val CAA = "https://coverartarchive.org"
    private var dir: File? = null
    private var owned: File? = null
    private val mem = LruCache<String, Bitmap>(8)
    private val missing = HashSet<String>() // "non trovato" per la sessione

    // Ogni fonte restituisce gli URL candidati (vuoto = niente); IOException = rete/MusicBrainz in errore
    private class Ctx { var error = false; var mbid: String? = null }

    fun init(cacheDir: File, filesDir: File) {
        File(cacheDir, "artwork").deleteRecursively() // cache solo iTunes della v0.5
        File(cacheDir, "artwork2").deleteRecursively() // v0.5.x: solo CAA a ricerca testuale
        dir = File(cacheDir, "artwork3").apply { mkdirs() }
        owned = File(filesDir, "covers").apply { mkdirs() }
    }

    suspend fun load(album: AlbumRef?, t: Track): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val first = firstArtist(t.artist)
            if (first.isEmpty()) return@withContext null
            val id = album?.releaseId?.takeIf { album.source == AlbumSource.OWNED }
            val key = if (id != null) "o$id" else base(if (album != null) "$first|${album.title}" else "$first|${t.title}")
            mem.get(key)?.let { return@withContext it }
            synchronized(missing) { if (key in missing) return@withContext null }
            val f = if (id != null) {
                val user = File(owned ?: return@withContext null, "$id.user.jpg")
                if (user.exists()) user else File(owned!!, "$id.jpg").also { f ->
                    if (!f.exists()) {
                        val c = Ctx()
                        if (!fetchOwned(id, album!!.artist, album.title, album.discogsImage, f, c)) {
                            if (!c.error) synchronized(missing) { missing += key } // rete in errore → riprova
                            return@withContext null
                        }
                    }
                }
            } else {
                val f = File(dir ?: return@withContext null, hash(key) + ".jpg")
                if (!f.exists()) {
                    val c = Ctx()
                    val b = firstImage(otherSources(firstArtistRaw(t.artist), t.title, album?.title, album?.discogsImage, c), c)
                    if (b == null) {
                        if (!c.error) synchronized(missing) { missing += key } // rete in errore → riprova al prossimo brano
                        return@withContext null
                    }
                    f.writeBytes(b)
                }
                f
            }
            decode(f)?.also { mem.put(key, it) } ?: run { f.delete(); null } // file corrotto → riscarica la prossima volta
        } catch (_: Exception) { null }
    }

    // ---- dischi posseduti ----

    private suspend fun fetchOwned(id: Long, artist: String, title: String, discogsImage: String?, f: File, c: Ctx = Ctx()): Boolean {
        val b = firstImage(ownedSources(id, artist, title, discogsImage, c, false), c) ?: return false
        f.writeBytes(b)
        return true
    }

    // Sync: scarica e salva (se manca)
    suspend fun prefetchOwned(r: OwnedRelease) = withContext(Dispatchers.IO) {
        val d = owned ?: return@withContext
        val f = File(d, "${r.id}.jpg")
        try { if (!f.exists()) fetchOwned(r.id, r.artist, r.title, r.coverImage, f); thumb(r.id) } catch (_: Exception) { /* ponytail: copertina assente → si ritenta al prossimo sync */ }
    }

    // Anteprima 300 px (generata dal file utente o automatico); null = nessuna copertina
    suspend fun thumb(id: Long): Bitmap? = withContext(Dispatchers.IO) {
        val d = owned ?: return@withContext null
        val t = File(d, "${id}_t.jpg")
        if (!t.exists()) {
            val src = File(d, "$id.user.jpg").takeIf { it.exists() } ?: File(d, "$id.jpg").takeIf { it.exists() } ?: return@withContext null
            val b = decode(src, THUMB) ?: return@withContext null
            val s = Bitmap.createScaledBitmap(b, THUMB, (THUMB * b.height / b.width.toFloat()).toInt().coerceAtLeast(1), true)
            try { t.outputStream().use { s.compress(Bitmap.CompressFormat.JPEG, 85, it) } } catch (_: Exception) { t.delete() }
            return@withContext s
        }
        BitmapFactory.decodeFile(t.path)
    }

    fun setOverride(id: Long, bytes: ByteArray) {
        val d = owned ?: return
        File(d, "$id.user.jpg").writeBytes(bytes)
        File(d, "${id}_t.jpg").delete(); mem.remove("o$id")
    }

    fun clearOverride(id: Long) {
        val d = owned ?: return
        File(d, "$id.user.jpg").delete(); File(d, "${id}_t.jpg").delete(); mem.remove("o$id")
    }

    // release uscita dalla collezione: via tutti i file (anche l'override)
    fun deleteOwned(id: Long) {
        val d = owned ?: return
        listOf("$id.jpg", "$id.user.jpg", "${id}_t.jpg").forEach { File(d, it).delete() }
        mem.remove("o$id")
    }

    fun invalidateOwned() { mem.evictAll(); synchronized(missing) { missing.clear() } }

    // Tutte le alternative per l'editor (le URL CAA non sono verificate: l'anteprima mancante si nasconde)
    suspend fun candidates(r: OwnedRelease): List<Candidate> = withContext(Dispatchers.IO) {
        val c = Ctx()
        ownedSources(r.id, r.artist, r.title, r.coverImage, c, true).flatMap { (name, urls) ->
            try { urls().map { Candidate(name, it) } } catch (_: Exception) { emptyList() }
        }
    }

    suspend fun preview(url: String): Bitmap? = withContext(Dispatchers.IO) {
        fetch(url)?.let { b ->
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(b, 0, b.size, o)
            var s = 1
            while (o.outWidth / (s * 2) >= THUMB) s *= 2
            BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = s })
        }
    }

    suspend fun bytes(url: String): ByteArray? = withContext(Dispatchers.IO) { fetch(url) }

    private fun ownedSources(id: Long, artist: String, title: String, discogsImage: String?, c: Ctx, picker: Boolean): List<Source> {
        val first = firstArtistRaw(artist)
        val l = ArrayList<Source>()
        l += "Cover Art Archive" to { listOfNotNull(mbRelease(id, c)?.let { "$CAA/release/$it/front-1200" }) }
        l += "Last.fm" to { listOfNotNull(lastFmCover(first, title)) }
        l += "Cover Art Archive (group)" to { rgUrls(first, "", title, c) }
        l += "Deezer" to { listOfNotNull(deezerAlbum(first, title)) }
        if (picker) l += "iTunes" to { itunes(first, "", title) }
        l += "Discogs" to {
            if (picker) try { // tutte le immagini della release
                val a = DiscogsApi.get("/releases/$id").optJSONArray("images")
                (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it).optString("uri") }.filter { it.isNotEmpty() }
            } catch (_: Exception) { listOfNotNull(discogsImage) } else listOfNotNull(discogsImage)
        }
        return l
    }

    // MusicBrainz url lookup: release collegata alla release Discogs (404 = nessun collegamento)
    private suspend fun mbRelease(id: Long, c: Ctx): String? {
        c.mbid?.let { return it }
        val r = URLEncoder.encode("https://www.discogs.com/release/$id", "UTF-8")
        val o = MusicBrainz.get("url?resource=$r&inc=release-rels&fmt=json") ?: throw IOException()
        val rels = o.optJSONArray("relations")
        for (i in 0 until (rels?.length() ?: 0)) {
            rels!!.getJSONObject(i).optJSONObject("release")?.optString("id")?.takeIf { it.isNotEmpty() }?.let { c.mbid = it; return it }
        }
        return null
    }

    // ---- altri brani ----

    private fun otherSources(first: String, title: String, album: String?, discogsImage: String?, c: Ctx): List<Source> {
        val l = ArrayList<Source>()
        if (album != null) l += "Last.fm" to { listOfNotNull(lastFmCover(first, album)) }
        l += "Cover Art Archive" to { rgUrls(first, title, album, c) }
        l += "Deezer" to { listOfNotNull(if (album != null) deezerAlbum(first, album) else deezerTrack(first, title)) }
        l += "iTunes" to { itunes(first, title, album) }
        l += "Discogs" to { listOfNotNull(discogsImage) }
        return l
    }

    // ---- fonti ----

    private suspend fun firstImage(sources: List<Source>, c: Ctx): ByteArray? {
        for ((_, urls) in sources) {
            val u = try { urls() } catch (_: IOException) { c.error = true; emptyList() } catch (_: Exception) { emptyList() }
            for (x in u) fetch(x)?.let { return it } // 404 = senza copertina → prossimo candidato
        }
        return null
    }

    private suspend fun lastFmCover(first: String, album: String): String? =
        if (BuildConfig.LASTFM_API_KEY.isEmpty()) null else LastFmApi.albumImage(first, album)

    private fun artistOk(o: JSONObject, first: String): Boolean {
        val ac = o.optJSONArray("artist-credit") ?: return false
        return (0 until ac.length()).any { base(ac.getJSONObject(it).optString("name")).contains(firstArtist(first)) }
    }

    // Release group dall'album (o dalla release MusicBrainz già trovata), altrimenti dalle release che contengono il brano.
    // Album in studio prima (primary Album senza secondary-types), poi gli altri. Max 3 candidati.
    private suspend fun rgUrls(first: String, title: String, album: String?, c: Ctx): List<String> {
        val ids = LinkedHashSet<String>()
        c.mbid?.let { mb ->
            val o = MusicBrainz.get("release/$mb?inc=release-groups&fmt=json") ?: throw IOException()
            o.optJSONObject("release-group")?.optString("id")?.takeIf { it.isNotEmpty() }?.let { ids += it }
        }
        val studio = ArrayList<String>(); val other = ArrayList<String>()
        fun add(g: JSONObject) {
            val id = g.optString("id")
            if (id.isEmpty()) return
            val isStudio = g.optString("primary-type") == "Album" && (g.optJSONArray("secondary-types")?.length() ?: 0) == 0
            (if (isStudio) studio else other) += id
        }
        if (ids.isEmpty() && album != null) {
            val o = MusicBrainz.search("release-group", "releasegroup:\"${MusicBrainz.esc(album)}\" AND artist:\"${MusicBrainz.esc(first)}\"")
                ?: throw IOException()
            val a = o.optJSONArray("release-groups")
            for (i in 0 until (a?.length() ?: 0)) {
                val g = a!!.getJSONObject(i)
                if (g.optInt("score") >= 90 && artistOk(g, first)) add(g)
            }
        }
        if (ids.isEmpty() && studio.isEmpty() && other.isEmpty() && title.isNotEmpty()) {
            val o = MusicBrainz.search("recording", "recording:\"${MusicBrainz.esc(stripFeat(title))}\" AND artist:\"${MusicBrainz.esc(first)}\"")
                ?: throw IOException()
            val a = o.optJSONArray("recordings")
            for (i in 0 until (a?.length() ?: 0)) {
                val r = a!!.getJSONObject(i)
                if (r.optInt("score") < 90 || !artistOk(r, first)) continue
                val rels = r.optJSONArray("releases") ?: continue
                for (j in 0 until rels.length()) rels.getJSONObject(j).optJSONObject("release-group")?.let(::add)
            }
        }
        ids += studio; ids += other
        return ids.take(3).map { "$CAA/release-group/$it/front-1200" }
    }

    // Deezer: cover_xl, scartando il segnaposto (md5 vuoto)
    private fun deezerCover(o: JSONObject?): String? = o?.optString("cover_xl")?.takeIf { it.isNotEmpty() && "/images/cover//" !in it }

    private fun deezerList(path: String): List<JSONObject> {
        val body = httpGet("https://api.deezer.com/$path")?.toString(Charsets.UTF_8) ?: return emptyList()
        val a = JSONObject(body).optJSONArray("data") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    private fun deezerAlbum(first: String, album: String): String? {
        val fk = firstArtist(first); val ak = normKey(Track(album, ""))
        return deezerList("search/album?q=${enc("$first $album")}").firstOrNull {
            artistMatch(it.optJSONObject("artist")?.optString("name").orEmpty(), fk) && normKey(Track(it.optString("title"), "")) == ak
        }.let(::deezerCover)
    }

    // senza album: brano, preferendo album.type == "album"
    private fun deezerTrack(first: String, title: String): String? {
        val fk = firstArtist(first); val tk = normKey(Track(title, ""))
        val ok = deezerList("search?q=${enc("$first ${stripFeat(title)}")}").filter {
            artistMatch(it.optJSONObject("artist")?.optString("name").orEmpty(), fk) && normKey(Track(it.optString("title"), "")) == tk && deezerCover(it.optJSONObject("album")) != null
        }
        return deezerCover((ok.firstOrNull { it.getJSONObject("album").optString("type") == "album" } ?: ok.firstOrNull())?.optJSONObject("album"))
    }

    // Candidati con artista coerente; scarta Single/EP (" - Single", " - EP", ≤ 1 brano) se ci sono alternative
    private fun itunes(first: String, title: String, album: String?): List<String> {
        val q = if (album != null) "term=${enc("$first $album")}&entity=album" else "term=${enc("$first $title")}&entity=song"
        val body = httpGet("https://itunes.apple.com/search?$q&limit=5&media=music")?.toString(Charsets.UTF_8) ?: return emptyList()
        val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
        val fk = firstArtist(first)
        val ok = (0 until arr.length()).map { arr.getJSONObject(it) }.filter { artistMatch(it.optString("artistName"), fk) && it.optString("artworkUrl100").isNotEmpty() }
        fun good(r: JSONObject): Boolean {
            val n = r.optString("collectionName")
            return !n.endsWith(" - Single") && !n.endsWith(" - EP") && r.optInt("trackCount", 2) > 1
        }
        return (ok.filter(::good) + ok.filterNot(::good)).map { it.getString("artworkUrl100").replace("100x100bb", "1200x1200bb") }
    }

    // ---- utilità ----

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // null = errore o non è un'immagine
    private fun fetch(url: String): ByteArray? = try {
        httpGet(url, if ("discogs.com" in url) DiscogsApi.UA else MusicBrainz.UA)?.takeIf {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(it, 0, it.size, o)
            o.outWidth > 0
        }
    } catch (_: Exception) { null }

    private fun decode(f: File, side: Int = SIDE): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= side) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    private fun hash(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
