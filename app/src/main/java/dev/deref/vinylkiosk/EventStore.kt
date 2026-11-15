package dev.deref.vinylkiosk

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// ponytail: solo in memoria (+ jsonl scritto dal listener) → Room se serve persistenza
// Tutte le chiamate al motore avvengono sul main thread (callback del listener + Handler main).
object EventStore {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected
    private val _listens = MutableStateFlow<List<Listen>>(emptyList()) // più recente in fondo
    val listens: StateFlow<List<Listen>> = _listens
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack

    var onListen: (Listen) -> Unit = {} // hook del listener per la scrittura su file
    private val engine = ListeningEngine { l ->
        _listens.value = _listens.value + l
        onListen(l)
    }

    fun setConnected(v: Boolean) { _connected.value = v }
    fun clear() { _listens.value = emptyList() }

    fun match(title: String, artist: String, now: Long) { engine.onMatch(title, artist, now); _currentTrack.value = engine.displayTrack }
    fun beat(text: String, now: Long) { engine.onBeat(text, now); _currentTrack.value = engine.displayTrack }
    fun tick(now: Long) { engine.tick(now); _currentTrack.value = engine.displayTrack }
}
