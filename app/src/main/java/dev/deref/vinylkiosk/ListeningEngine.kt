package dev.deref.vinylkiosk

// Kotlin puro (niente android.*) → testabile su JVM.
data class Track(val title: String, val artist: String)

data class Listen(val track: Track, val firstBeat: Long, val lastBeat: Long, val beats: Int) {
    val start get() = firstBeat - 10_000 // stima tempo di riconoscimento
    val playedMs get() = lastBeat - firstBeat + 12_000
}

class ListeningEngine(private val onListen: (Listen) -> Unit) {

    private class Cur(val track: Track, val key: String, val first: Long, var last: Long, var beats: Int)
    // track == null → battito neutro
    private class Pend(val track: Track?, val key: String?, val first: Long, var last: Long, var count: Int)

    private val known = HashMap<String, Track>()
    private var current: Cur? = null
    private var pending: Pend? = null
    private var lastText: String? = null
    private var lastBeatTime = -1L
    private var away = 0 // battiti consecutivi diversi dal brano corrente

    val currentTrack: Track? get() = current?.track

    // Brano da mostrare a schermo: subito al primo battito, senza i filtri del salvataggio.
    // ponytail: un sample di 1 battito compare per ~12 s, accettato per non ritardare lo schermo
    var displayTrack: Track? = null
        private set

    fun onMatch(title: String, artist: String, time: Long) {
        val t = Track(title, artist)
        known["$title di $artist"] = t
        displayTrack = t
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
        if (t != null) displayTrack = t else if (current == null) displayTrack = null
        val key = t?.let(::normKey) // uguaglianza su chiave normalizzata
        val cur = current
        if (cur != null && key == cur.key) {
            // fino a MAX_AWAY battiti di altro (sample, passaggi piani) non spezzano il tratto
            pending = null
            away = 0
            cur.last = time
            cur.beats++
            return
        }
        away++
        val p = pending
        val np = if (p != null && p.key == key) p.also { it.count++; it.last = time } else Pend(t, key, time, time, 1)
        pending = np
        // un altro brano per 2 battiti di fila, o troppi battiti lontani dal brano corrente → chiudi
        if ((np.track != null && np.count >= 2) || away > MAX_AWAY) close()
        if (np.track != null && np.count >= 2) {
            current = Cur(np.track, np.key!!, np.first, np.last, np.count)
            pending = null
            away = 0
        }
    }

    fun tick(now: Long) {
        if (lastBeatTime >= 0 && now - lastBeatTime > 30_000) { close(); pending = null }
    }

    fun finish() = close()

    private fun close() {
        val c = current ?: return
        current = null
        if (displayTrack == c.track) displayTrack = null
        away = 0
        val l = Listen(c.track, c.first, c.last, c.beats)
        if (l.playedMs >= MIN_PLAYED_MS) onListen(l) // Last.fm: niente scrobble sotto i 30 s
    }

    private companion object {
        const val MAX_AWAY = 2 // ~25 s di silenzio/sample tollerati dentro un brano
        const val MIN_PLAYED_MS = 30_000L
    }
}
