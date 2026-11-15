package dev.deref.vinylkiosk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// eval == null → durata in ricerca…
data class EvaluatedListen(val listen: Listen, val eval: Evaluation? = null)

// ponytail: solo in memoria (+ jsonl scritto dal listener) → Room se serve persistenza
// Tutte le chiamate al motore avvengono sul main thread (callback del listener + Handler main).
object EventStore {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected
    private val _listens = MutableStateFlow<List<EvaluatedListen>>(emptyList()) // più recente in fondo
    val listens: StateFlow<List<EvaluatedListen>> = _listens
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack

    var onEvaluated: (Listen, Evaluation) -> Unit = { _, _ -> } // hook del listener per la scrittura su file
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine = ListeningEngine { l ->
        _listens.update { it + EvaluatedListen(l) }
        scope.launch {
            val e = evaluate(l, Durations.duration(l.track))
            _listens.update { list -> list.map { if (it.listen === l) it.copy(eval = e) else it } }
            onEvaluated(l, e)
        }
    }

    fun setConnected(v: Boolean) { _connected.value = v }
    fun clear() { _listens.value = emptyList() }

    fun match(title: String, artist: String, now: Long) { engine.onMatch(title, artist, now); _currentTrack.value = engine.displayTrack }
    fun beat(text: String, now: Long) { engine.onBeat(text, now); _currentTrack.value = engine.displayTrack }
    fun tick(now: Long) { engine.tick(now); _currentTrack.value = engine.displayTrack }
}
