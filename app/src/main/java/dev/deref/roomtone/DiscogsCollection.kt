package dev.deref.roomtone

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class OwnedTrack(val title: String, val artist: String?, val durationMs: Long?, val position: String? = null)
data class OwnedRelease(
    val id: Long, val masterId: Long?, val title: String, val artist: String, val year: Int,
    val formats: List<String>, val tracks: List<OwnedTrack>, val coverImage: String?,
)

data class CollectionProgress(
    val running: Boolean = false, val releases: Int = 0,
    val detailsDone: Int = 0, val detailsTotal: Int = 0,
    val coversDone: Int = 0, val coversTotal: Int = 0, val error: String? = null,
)

// Account Discogs + collezione (cartella "All", sync incrementale e riprendibile) + indice brano → release posseduta.
object DiscogsCollection {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dir: File
    private lateinit var state: SharedPreferences // lastSync
    private var job: Job? = null

    private val _user = MutableStateFlow<String?>(null)
    val user: StateFlow<String?> = _user
    private val _progress = MutableStateFlow(CollectionProgress())
    val progress: StateFlow<CollectionProgress> = _progress
    private val _releases = MutableStateFlow<List<OwnedRelease>>(emptyList())
    val releases: StateFlow<List<OwnedRelease>> = _releases

    @Volatile private var index = emptyMap<String, OwnedRelease>()
    private val file get() = File(dir, "discogs_collection.json")

    @Synchronized fun init(ctx: Context) { // idempotente
        if (::dir.isInitialized) return
        val app = ctx.applicationContext
        dir = app.filesDir
        state = app.getSharedPreferences("discogs_sync", Context.MODE_PRIVATE)
        DiscogsApi.init(app)
        _user.value = DiscogsApi.savedUser()
        // ponytail: lettura sincrona sul thread chiamante → caricare in background se la collezione diventa enorme
        try { setAll(load(JSONObject(file.readText()).getJSONArray("releases"))) } catch (_: Exception) { /* assente → vuota */ }
        _progress.value = CollectionProgress(releases = _releases.value.size)
    }

    suspend fun login(token: String, verifier: String) {
        _user.value = DiscogsApi.accessToken(token, verifier)
        start()
    }

    fun logout() {
        val j = job
        scope.launch {
            j?.cancelAndJoin()
            DiscogsApi.clear()
            state.edit().clear().apply()
            file.delete()
            File(dir, "covers").apply { deleteRecursively(); mkdirs() } // Artwork continua a scriverci
            Artwork.invalidateOwned()
            setAll(emptyList())
            _user.value = null
            _progress.value = CollectionProgress()
        }
    }

    fun startIfStale() {
        if (_user.value != null && System.currentTimeMillis() - state.getLong("lastSync", 0) > 6 * 3_600_000L) start()
    }

    fun start() {
        if (_user.value == null || job?.isActive == true) return
        job = scope.launch { sync(_user.value ?: return@launch) }
    }

    // Release posseduta che contiene il brano: prima con l'artista completo, poi col solo primo artista
    fun owned(t: Track): OwnedRelease? = index[normKey(t)] ?: index[normKey(Track(t.title, firstArtistRaw(t.artist)))]

    fun byId(id: Long): OwnedRelease? = _releases.value.firstOrNull { it.id == id }

    private fun setAll(list: List<OwnedRelease>) {
        val m = HashMap<String, OwnedRelease>()
        for (r in list) for (t in r.tracks) { // più release per chiave → prima corrispondenza
            val a = t.artist ?: r.artist
            m.putIfAbsent(normKey(Track(t.title, a)), r)
            m.putIfAbsent(normKey(Track(t.title, firstArtistRaw(a))), r)
        }
        index = m
        _releases.value = list
    }

    private fun load(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
        OwnedRelease(
            o.getLong("id"), o.optLong("masterId").takeIf { it > 0 }, o.getString("title"), o.getString("artist"), o.optInt("year"),
            o.getJSONArray("formats").let { f -> (0 until f.length()).map(f::getString) },
            o.getJSONArray("tracks").let { ts -> (0 until ts.length()).map { ts.getJSONObject(it) }.map { t ->
                OwnedTrack(t.getString("title"), t.optString("artist").takeIf { it.isNotEmpty() }, t.optLong("durationMs").takeIf { it > 0 }, t.optString("position").takeIf { it.isNotEmpty() })
            } },
            o.optString("coverImage").takeIf { it.isNotEmpty() },
        )
    }

    private fun save(list: List<OwnedRelease>) {
        val a = JSONArray()
        list.forEach { r ->
            a.put(JSONObject().put("id", r.id).put("masterId", r.masterId ?: 0).put("title", r.title).put("artist", r.artist)
                .put("year", r.year).put("formats", JSONArray(r.formats)).put("coverImage", r.coverImage ?: "")
                .put("tracks", JSONArray().also { ts -> r.tracks.forEach { t ->
                    ts.put(JSONObject().put("title", t.title).put("artist", t.artist ?: "").put("durationMs", t.durationMs ?: 0).put("position", t.position ?: ""))
                } }))
        }
        try {
            val tmp = File(dir, "discogs_collection.tmp")
            tmp.writeText(JSONObject().put("releases", a).toString())
            tmp.renameTo(file)
        } catch (_: Exception) { /* ponytail: scrittura fallita → in memoria, si rifà al prossimo sync */ }
    }

    private fun strings(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getString(it) }

    // "3:45" → ms; vuota/illeggibile → null
    private fun duration(s: String): Long? {
        val p = s.split(':').map { it.toLongOrNull() ?: return null }
        if (p.isEmpty() || p.size > 3) return null
        return p.fold(0L) { acc, n -> acc * 60 + n } * 1000 // ponytail: "1:02:03" gestito, altri formati → null
    }

    private suspend fun sync(user: String) {
        _progress.value = CollectionProgress(running = true, releases = _releases.value.size)
        try {
            // 1) elenco (tutte le pagine)
            val basics = LinkedHashMap<Long, JSONObject>() // dedup: stessa release in più copie
            var page = 1
            while (true) {
                val o = DiscogsApi.get("/users/${java.net.URLEncoder.encode(user, "UTF-8")}/collection/folders/0/releases", mapOf("per_page" to "100", "page" to page.toString()))
                val a = o.optJSONArray("releases")
                for (i in 0 until (a?.length() ?: 0)) a!!.getJSONObject(i).let { basics.putIfAbsent(it.getLong("id"), it.getJSONObject("basic_information")) }
                _progress.value = _progress.value.copy(releases = basics.size)
                if (page >= (o.optJSONObject("pagination")?.optInt("pages") ?: 1)) break
                page++
            }
            // 2) rimosse dalla collezione → via dalla cache (e copertine, anche scelte dall'utente)
            // cache precedente alla v0.6.1 senza posizioni (A1, B1…) → dettagli riscaricati una volta
            val cached = _releases.value.filter { r -> r.tracks.any { it.position != null } }.associateBy { it.id }
            val kept = cached.values.filter { it.id in basics }
            _releases.value.map { it.id }.filter { it !in basics }.forEach { Artwork.deleteOwned(it) }
            // ponytail: release senza posizioni anche su Discogs → riscaricate a ogni sync (rare)
            // 3) dettagli delle nuove
            val fresh = basics.keys.filter { it !in cached }
            val list = ArrayList(kept)
            if (kept.size != cached.size) { setAll(list); save(list) }
            _progress.value = _progress.value.copy(releases = basics.size, detailsTotal = fresh.size)
            for ((n, id) in fresh.withIndex()) {
                val b = basics.getValue(id)
                try { list += release(id, b) } catch (e: DiscogsHttpException) { if (e.code != 404) throw e } // 404 = release rimossa da Discogs → saltata
                _progress.value = _progress.value.copy(detailsDone = n + 1)
                if ((n + 1) % 20 == 0) { setAll(list); save(list) } // salvataggio progressivo
            }
            setAll(list); save(list)
            // 4) copertine
            _progress.value = _progress.value.copy(coversTotal = list.size)
            // ponytail: release senza copertina trovata si ritentano a ogni sync → negativi in cache se pesa
            list.forEachIndexed { i, r -> Artwork.prefetchOwned(r); _progress.value = _progress.value.copy(coversDone = i + 1) }
            state.edit().putLong("lastSync", System.currentTimeMillis()).apply()
            _progress.value = CollectionProgress(releases = list.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _progress.value = CollectionProgress(releases = _releases.value.size, error = e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun release(id: Long, b: JSONObject): OwnedRelease {
        val o = DiscogsApi.get("/releases/$id")
        val ta = b.optJSONArray("artists")
        val artist = (0 until (ta?.length() ?: 0)).joinToString(" & ") { cleanDiscogsArtist(ta!!.getJSONObject(it).optString("name")) }
        val fm = b.optJSONArray("formats")
        val formats = (0 until (fm?.length() ?: 0)).flatMap { fm!!.getJSONObject(it).let { f -> listOf(f.optString("name")) + strings(f.optJSONArray("descriptions")) } }.filter { it.isNotEmpty() }
        val tl = o.optJSONArray("tracklist")
        val tracks = (0 until (tl?.length() ?: 0)).map { tl!!.getJSONObject(it) }.filter { it.optString("type_") == "track" }.map { t ->
            val xa = t.optJSONArray("artists")
            OwnedTrack(
                t.optString("title"),
                xa?.let { a -> (0 until a.length()).joinToString(" & ") { cleanDiscogsArtist(a.getJSONObject(it).optString("name")) } }?.takeIf { it.isNotEmpty() },
                duration(t.optString("duration")),
                t.optString("position").takeIf { it.isNotEmpty() },
            )
        }
        return OwnedRelease(id, b.optLong("master_id").takeIf { it > 0 }, b.optString("title"), artist, b.optInt("year"), formats, tracks,
            b.optString("cover_image").takeIf { it.isNotEmpty() })
    }
}
