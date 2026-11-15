package dev.deref.vinylkiosk

// Kotlin puro (niente android.*) → testabile su JVM.
data class Track(val title: String, val artist: String)

data class Listen(val track: Track, val firstBeat: Long, val lastBeat: Long, val beats: Int) {
    val start get() = firstBeat - 10_000 // stima tempo di riconoscimento
    val playedMs get() = lastBeat - firstBeat + 12_000
}

class ListeningEngine(private val onListen: (Listen) -> Unit) {

    private class Cur(val track: Track, val first: Long, var last: Long, var beats: Int)
    // track == null → battito neutro
    private class Pend(val track: Track?, val first: Long, var last: Long, var count: Int)

    private val known = HashMap<String, Track>()
    private var current: Cur? = null
    private var pending: Pend? = null
    private var lastText: String? = null
    private var lastBeatTime = -1L

    val currentTrack: Track? get() = current?.track

    fun onMatch(title: String, artist: String, time: Long) {
        known["$title di $artist"] = Track(title, artist)
    }

    private val neutral = Regex("""^\d+ canzon[ei] trovat[ae]$""")

    // null = neutro
    private fun resolve(text: String): Track? {
        if (neutral.matches(text) || text.startsWith("Shazam riconosce")) return null
        known[text]?.let { return it }
        // ponytail: titoli con " di " gestiti dalla mappa dei match → fallback euristico
        val i = text.lastIndexOf(" di ")
        return if (i < 0) null else Track(text.substring(0, i), text.substring(i + 4))
    }

    fun onBeat(text: String, time: Long) {
        if (text == lastText && time - lastBeatTime <= 2_000) return // doppione
        if (lastBeatTime >= 0 && time - lastBeatTime > 30_000) { close(); pending = null }
        lastText = text
        lastBeatTime = time
        val t = resolve(text)
        val cur = current
        if (cur != null && t == cur.track) {
            pending = null // interruzione di 1 battito (sample/neutro) ignorata
            cur.last = time
            cur.beats++
            return
        }
        val p = pending
        val np = if (p != null && p.track == t) p.also { it.count++; it.last = time } else Pend(t, time, time, 1)
        pending = np
        if (np.count >= 2) {
            close()
            if (np.track != null) {
                current = Cur(np.track, np.first, np.last, np.count)
                pending = null
            } // neutro: pending resta
        }
    }

    fun tick(now: Long) {
        if (lastBeatTime >= 0 && now - lastBeatTime > 30_000) { close(); pending = null }
    }

    fun finish() = close()

    private fun close() {
        val c = current ?: return
        current = null
        if (c.beats >= 2) onListen(Listen(c.track, c.first, c.last, c.beats))
    }
}
