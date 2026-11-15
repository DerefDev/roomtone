package dev.deref.vinylkiosk

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Recognition(
    val title: String,
    val artist: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val updates: Int,
    val notiKey: String,
)

// ponytail: solo in memoria, si perde al riavvio → Room/file se serve persistenza
object EventStore {
    val recognitions = MutableStateFlow<List<Recognition>>(emptyList()) // più recente in fondo
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    fun setConnected(v: Boolean) { _connected.value = v }
    fun clear() { recognitions.value = emptyList() }

    // ponytail: dedup banale (ultimo elemento, stessa key+titolo+artista) → logica a durate poi
    fun add(title: String, artist: String, key: String, now: Long) {
        val list = recognitions.value
        val last = list.lastOrNull()
        recognitions.value = if (last != null && last.notiKey == key && last.title == title && last.artist == artist) {
            list.dropLast(1) + last.copy(lastSeen = now, updates = last.updates + 1)
        } else {
            list + Recognition(title, artist, now, now, 0, key)
        }
    }
}
