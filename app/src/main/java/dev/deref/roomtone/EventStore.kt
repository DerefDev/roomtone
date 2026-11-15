package dev.deref.roomtone

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

// eval == null → durata in ricerca…
data class EvaluatedListen(val listen: Listen, val eval: Evaluation? = null, val meta: ScrobbleMeta? = null)

// Brano in corso: startedAt = istante in cui compare a schermo (≈ primo battito del tratto);
// durationMs e meta si completano in seguito (meta subito, durata dopo la ricerca in rete).
data class NowPlaying(val track: Track, val startedAt: Long, val durationMs: Long? = null, val meta: ScrobbleMeta? = null)

// ponytail: solo in memoria (+ jsonl scritto dal listener) → Room se serve persistenza
// Tutte le chiamate al motore avvengono sul main thread (callback del listener + Handler main).
object EventStore {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected
    private val _listens = MutableStateFlow<List<EvaluatedListen>>(emptyList()) // più recente in fondo
    val listens: StateFlow<List<EvaluatedListen>> = _listens
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying

    var onEdited: (Listen, Evaluation, ScrobbleMeta) -> Unit = { _, _, _ -> } // idem per le modifiche da UI
    var onEvaluated: (Listen, Evaluation, ScrobbleMeta) -> Unit = { _, _, _ -> } // hook del listener per la scrittura su file
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine = ListeningEngine(if (Locale.getDefault().language == "it") " di " else " by ") { l ->
        _listens.update { it + EvaluatedListen(l) }
        scope.launch {
            val e = evaluate(l, Durations.duration(l.track))
            val m = scrobbleMeta(l.track, HistorySync.index.lookup(l.track)) // cronologia → altrimenti Shazam
            _listens.update { list -> list.map { if (it.listen === l) it.copy(eval = e, meta = m) else it } }
            onEvaluated(l, e, m)
        }
    }

    fun setConnected(v: Boolean) { _connected.value = v }
    fun clear() { _listens.value = emptyList() }

    // Bozza confermata (VALID, meta/ripetizioni corretti) o scartata (DISCARDED, plays 0).
    // ponytail: solo in memoria + riga "edit" nel jsonl → coda persistente v0.6
    fun resolve(l: Listen, status: Status, meta: ScrobbleMeta? = null, plays: Int? = null) {
        val cur = _listens.value.firstOrNull { it.listen === l && it.eval != null } ?: return
        val e = cur.eval!!
        val n = if (status == Status.DISCARDED) 0 else plays ?: e.plays
        val ne = e.copy(status = status, plays = n, timestamps = List(n) { l.start + it * (e.durationMs ?: 0L) })
        val nm = meta ?: cur.meta ?: scrobbleMeta(l.track, null)
        _listens.update { list -> list.map { if (it.listen === l) it.copy(eval = ne, meta = nm) else it } }
        onEdited(l, ne, nm)
    }

    fun match(title: String, artist: String, now: Long) { engine.onMatch(title, artist, now); publish(now) }
    fun beat(text: String, now: Long) { engine.onBeat(text, now); publish(now) }
    fun tick(now: Long) { engine.tick(now); publish(now) }

    private fun publish(now: Long) {
        val t = engine.displayTrack
        _currentTrack.value = t
        if (t == null) { _nowPlaying.value = null; return }
        if (_nowPlaying.value?.track == t) return
        val np = NowPlaying(t, now)
        _nowPlaying.value = np
        scope.launch {
            fun same(c: NowPlaying?) = c != null && c.track == t && c.startedAt == np.startedAt
            val m = scrobbleMeta(t, HistorySync.index.lookup(t))
            _nowPlaying.update { if (same(it)) it!!.copy(meta = m) else it }
            val d = Durations.duration(t)
            _nowPlaying.update { if (same(it)) it!!.copy(durationMs = d) else it }
        }
    }
}
