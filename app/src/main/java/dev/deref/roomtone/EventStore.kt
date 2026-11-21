package dev.deref.roomtone

import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// eval == null → durata in ricerca…
data class EvaluatedListen(val listen: Listen, val eval: Evaluation? = null, val meta: ScrobbleMeta? = null,
    val options: List<Version> = emptyList()) // versioni in cronologia tra cui scegliere (vuoto = nessuna scelta)

// Versione da usare: scelta/automatica, altrimenti la più ascoltata (default in attesa della scelta); null → Shazam
private fun versionOf(p: Pick?) = when (p) { is Pick.Auto -> p.v; is Pick.Ask -> p.options[0]; null -> null }

private const val CONTEXT_MS = 30 * 60_000L

// Metadati di un brano: cronologia (anche via titolo/artista come scritti su Discogs) → disco Discogs → Shazam.
// Terzo valore: true se la cronologia non ha versioni.
private fun metaFor(t: Track, own: Pair<OwnedRelease, OwnedTrack>?): Triple<ScrobbleMeta, Pick?, Boolean> {
    val ot = own?.let { Track(it.second.title, it.second.artist ?: it.first.artist) }
    val vs = mergeVersions(HistorySync.index.versions(t), ot?.let { HistorySync.index.versions(it) }.orEmpty())
    val p = pick(vs, VersionChoices.get(t) ?: ot?.let { VersionChoices.get(it) })
    val m = versionOf(p)?.let { scrobbleMeta(t, it) }
        ?: if (ot != null) ScrobbleMeta(ot.artist, ot.title, null, MetaSource.DISCOGS) else scrobbleMeta(t, null)
    return Triple(m, p, vs.isEmpty())
}

// Brano in corso: startedAt = istante in cui compare a schermo (≈ primo battito del tratto);
// durationMs e meta si completano in seguito (meta subito, durata dopo la ricerca in rete).
// album = disco risolto (albumDone = ricerca conclusa, anche senza esito).
data class NowPlaying(
    val track: Track, val startedAt: Long, val durationMs: Long? = null, val meta: ScrobbleMeta? = null,
    val album: AlbumRef? = null, val albumDone: Boolean = false, val next: String? = null, val groups: Set<String> = emptySet(),
    val ownedTitle: String? = null, // titolo esatto sul disco posseduto
)

// Album da scrobblare: modalità "owned" e disco posseduto → quello reale; altrimenti cronologia, poi album risolto.
fun withAlbum(m: ScrobbleMeta, ref: AlbumRef?, t: Track): ScrobbleMeta {
    if (Prefs.albumMode.value == "owned" && ref?.source == AlbumSource.OWNED) {
        DiscogsCollection.ownedTrack(t, ref.releaseId)?.let { (rel, tr) ->
            val o = Prefs.editOptions.value
            return ScrobbleMeta(cleanArtist(tr.artist ?: rel.artist, o, tr.title), cleanTitle(tr.title, o), cleanAlbum(rel.title, o),
                MetaSource.OWNED, AlbumSource.OWNED)
        }
    }
    val (a, src) = when {
        Prefs.albumMode.value == "owned" && ref?.source == AlbumSource.OWNED -> ref.title to AlbumSource.OWNED
        // scrobble coerente con Last.fm: versione dalla cronologia → il suo album, anche se assente
        m.source == MetaSource.HISTORY -> m.album to m.album?.let { AlbumSource.HISTORY }
        ref != null -> ref.title to ref.source
        else -> null to null
    }
    return m.copy(album = a, albumSource = src)
}

// Ascolti valutati persistiti in listens.json (bozze + ultimi 7 giorni); ponytail: file unico riscritto a ogni modifica → Room se cresce
// Tutte le chiamate al motore avvengono sul main thread (callback del listener + Handler main).
// Shazam scrive le notifiche nella lingua del sistema, non in quella scelta per Roomtone
private fun shazamLanguage() = android.content.res.Resources.getSystem().configuration.locales[0].language

object EventStore {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected
    private val _listens = MutableStateFlow<List<EvaluatedListen>>(emptyList()) // più recente in fondo
    val listens: StateFlow<List<EvaluatedListen>> = _listens
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying

    var onEdited: (Listen, Evaluation, ScrobbleMeta) -> Unit = { _, _, _ -> } // idem per le modifiche da UI
    var onEvaluated: (Listen, Evaluation, ScrobbleMeta) -> Unit = { _, _, _ -> } // hook del listener per la scrittura su file
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Disco in ascolto: valido 30 min dall'ultimo brano riconosciuto su un disco posseduto
    @Volatile private var currentRecord: OwnedRelease? = null
    @Volatile private var recordSeenAt = 0L
    private fun context(now: Long) = currentRecord?.takeIf { now - recordSeenAt < CONTEXT_MS }
    private fun seen(own: Pair<OwnedRelease, OwnedTrack>?, now: Long) { if (own != null) { currentRecord = own.first; recordSeenAt = now } }

    private val engine = ListeningEngine(if (shazamLanguage() == "it") " di " else " by ") { l ->
        _listens.update { it + EvaluatedListen(l) }
        scope.launch {
            val ctx = context(l.lastBeat)
            val own = DiscogsCollection.findOwned(l.track, ctx)
            seen(own, l.lastBeat)
            val (m0, p, noHist) = metaFor(l.track, own)
            val m = withAlbum(m0, Albums.resolve(l.track, m0.album, own), l.track)
            var e = evaluate(l, Durations.duration(l.track, own?.second?.durationMs))
            if (ctx != null && own == null && noHist && e.status == Status.VALID) e = e.copy(status = Status.DRAFT, reason = Reason.NOT_ON_RECORD)
            val ask = p is Pick.Ask && m.source != MetaSource.OWNED // "disco reale": titolo/artista/album dal disco, la versione non conta
            if (ask && e.status == Status.VALID) e = e.copy(status = Status.DRAFT, reason = Reason.CHOOSE_VERSION)
            val opts = if (ask && e.status == Status.DRAFT) (p as Pick.Ask).options else emptyList()
            _listens.update { list -> list.map { if (it.listen === l) it.copy(eval = e, meta = m, options = opts) else it } }
            save()
            onEvaluated(l, e, m)
            if (e.status == Status.VALID && Prefs.scrobbling.value) ScrobbleQueue.enqueue(l, e, m) // scrobbling spento → mostrato ma non in coda
        }
    }

    private var file: File? = null

    @Synchronized fun init(dir: File) { // idempotente
        if (file != null) return
        file = File(dir, "listens.json")
        try {
            val a = JSONArray(file!!.readText())
            _listens.value = (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
        } catch (_: Exception) { /* assente o corrotto → vuoto */ }
    }

    private fun fromJson(o: JSONObject): EvaluatedListen {
        val ts = o.getJSONArray("timestamps")
        val m = o.getJSONObject("meta")
        return EvaluatedListen(
            Listen(Track(o.getString("title"), o.getString("artist")), o.getLong("firstBeat"), o.getLong("lastBeat"), o.getInt("beats")),
            Evaluation(Status.valueOf(o.getString("status")), Reason.valueOf(o.getString("reason")), o.getInt("plays"),
                List(ts.length()) { ts.getLong(it) }, if (o.isNull("durationMs")) null else o.getLong("durationMs")),
            ScrobbleMeta(m.getString("artist"), m.getString("title"), m.optStr("album"),
                MetaSource.valueOf(m.getString("source")), m.optStr("albumSource")?.let { AlbumSource.valueOf(it) }),
            o.optJSONArray("options")?.let { a -> List(a.length()) { i -> a.getJSONObject(i).let { v ->
                val r = v.getJSONArray("recent")
                Version(v.getString("artist"), v.getString("title"), v.optStr("album"), v.getInt("plays"), List(r.length()) { r.getLong(it) })
            } } }.orEmpty(),
        )
    }

    private fun toJson(x: EvaluatedListen): JSONObject {
        val l = x.listen; val e = x.eval!!; val m = x.meta!!
        return JSONObject().put("title", l.track.title).put("artist", l.track.artist).put("firstBeat", l.firstBeat)
            .put("lastBeat", l.lastBeat).put("beats", l.beats).put("status", e.status.name).put("reason", e.reason.name)
            .put("plays", e.plays).put("timestamps", JSONArray(e.timestamps)).put("durationMs", e.durationMs ?: JSONObject.NULL)
            .put("meta", JSONObject().put("artist", m.artist).put("title", m.title).put("album", m.album ?: JSONObject.NULL)
                .put("source", m.source.name).put("albumSource", m.albumSource?.name ?: JSONObject.NULL))
            .put("options", JSONArray().also { a -> x.options.forEach { v ->
                a.put(JSONObject().put("artist", v.artist).put("title", v.title).put("album", v.album ?: "").put("plays", v.plays).put("recent", JSONArray(v.recent)))
            } })
    }

    // Salva in background: tutte le bozze + VALID/DISCARDED degli ultimi 7 giorni (le più vecchie si potano).
    private fun save() {
        val f = file ?: return
        scope.launch { writeFile(f) }
    }

    // Stato letto al momento della scrittura (non allo scatto): l'ultima scrittura è sempre la più recente.
    @Synchronized private fun writeFile(f: File) {
        val min = System.currentTimeMillis() - 7 * 86_400_000L
        val a = JSONArray()
        _listens.value.filter { it.eval != null && it.meta != null && (it.eval.status == Status.DRAFT || it.listen.start >= min) }
            .forEach { a.put(toJson(it)) }
        try {
            val tmp = File(f.parentFile, "listens.tmp")
            tmp.writeText(a.toString())
            tmp.renameTo(f)
        } catch (_: Exception) { /* ponytail: scrittura fallita → si riprova al prossimo salvataggio */ }
    }

    fun setConnected(v: Boolean) { _connected.value = v }
    fun clear() { _listens.value = emptyList(); save() }

    // Bozza confermata (VALID, meta/ripetizioni corretti) o scartata (DISCARDED, plays 0).
    fun resolve(l: Listen, status: Status, meta: ScrobbleMeta? = null, plays: Int? = null) {
        val cur = _listens.value.firstOrNull { it.listen === l && it.eval != null } ?: return
        val e = cur.eval!!
        val n = if (status == Status.DISCARDED) 0 else plays ?: e.plays
        val ne = e.copy(status = status, plays = n, timestamps = List(n) { l.start + it * (e.durationMs ?: 0L) })
        val nm = meta ?: cur.meta ?: scrobbleMeta(l.track, null)
        _listens.update { list -> list.map { if (it.listen === l) it.copy(eval = ne, meta = nm) else it } }
        save()
        onEdited(l, ne, nm)
        if (status == Status.VALID) ScrobbleQueue.enqueue(l, ne, nm) // conferma esplicita → sempre in coda
    }

    // Versione scelta dall'utente: salvata per il brano; le bozze "scegli versione" dello stesso brano si risolvono (→ coda),
    // le altre bozze con opzioni ne aggiornano solo i metadati (conferma ancora a carico dell'utente).
    fun chooseVersion(l: Listen, v: Version) {
        VersionChoices.set(l.track, v.key)
        val hk = histKey(l.track)
        val meta = scrobbleMeta(l.track, v)
        _listens.value.filter { it.eval?.status == Status.DRAFT && it.options.isNotEmpty() && histKey(it.listen.track) == hk }.forEach { x ->
            val m = withAlbum(meta, null, x.listen.track)
            if (x.eval!!.reason == Reason.CHOOSE_VERSION) resolve(x.listen, Status.VALID, m)
            else _listens.update { list -> list.map { if (it.listen === x.listen) it.copy(meta = m) else it } }
        }
        save()
    }

    // Icona Shazam (anteprima finché la copertina non arriva); la prima notifica del match ne è priva → mai sovrascrivere con null
    private val icons = LruCache<Track, Bitmap>(8)
    private val _icon = MutableStateFlow<Bitmap?>(null)
    val icon: StateFlow<Bitmap?> = _icon

    fun match(title: String, artist: String, now: Long, icon: Bitmap? = null) {
        if (icon != null) icons.put(Track(title, artist), icon)
        engine.onMatch(title, artist, now); publish(now)
    }
    fun beat(text: String, now: Long) { engine.onBeat(text, now); publish(now) }
    fun tick(now: Long) { engine.tick(now); publish(now) }

    private fun publish(now: Long) {
        val t = engine.displayTrack
        _icon.value = t?.let(icons::get)
        if (t == null) { _nowPlaying.value = null; return }
        if (_nowPlaying.value?.track == t) return
        val np = NowPlaying(t, now)
        _nowPlaying.value = np
        scope.launch {
            fun same(c: NowPlaying?) = c != null && c.track == t && c.startedAt == np.startedAt
            fun upd(f: (NowPlaying) -> NowPlaying) = _nowPlaying.update { if (same(it)) f(it!!) else it }
            coroutineScope {
                launch {
                    val amp = ampParts(t)
                    if (amp.isNotEmpty()) { val g = Artists.resolve(amp); upd { it.copy(groups = g) } }
                }
                launch {
                    val own = DiscogsCollection.findOwned(t, context(now))
                    seen(own, now)
                    val m = metaFor(t, own).first
                    upd { it.copy(meta = m) }
                    val ref = Albums.resolve(t, m.album, own)
                    // titolo esatto della traccia sul disco posseduto (nessuna pulizia)
                    upd { it.copy(meta = withAlbum(m, ref, t), album = ref, albumDone = true, ownedTitle = own?.second?.title) }
                    val nx = Tracklists.next(t, ref)
                    upd { it.copy(next = nx) }
                }
                launch { val d = Durations.duration(t, DiscogsCollection.findOwned(t, context(now))?.second?.durationMs); upd { it.copy(durationMs = d) } }
            }
        }
    }
}
