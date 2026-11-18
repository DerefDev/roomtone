package dev.deref.roomtone

// Kotlin puro. Cronologia Last.fm aggregata per normKey → variante (artista, titolo) → album.
data class HistoryMatch(val artist: String, val title: String, val album: String?, val plays: Int)

enum class MetaSource { HISTORY, SHAZAM, OWNED }

// Metadati da inviare (v0.5) per un ascolto.
data class ScrobbleMeta(val artist: String, val title: String, val album: String?, val source: MetaSource, val albumSource: AlbumSource? = null)

fun scrobbleMeta(t: Track, h: HistoryMatch?) =
    if (h != null) ScrobbleMeta(h.artist, h.title, h.album, MetaSource.HISTORY)
    else ScrobbleMeta(t.artist, t.title, null, MetaSource.SHAZAM)

class HistoryIndex {
    private class Variant(val artist: String, val title: String) {
        var count = 0
        var lastUts = 0L
        val albums = HashMap<String, Int>() // "" = senza album
    }

    private val map = HashMap<String, HashMap<Pair<String, String>, Variant>>()
    var size = 0
        private set

    @Synchronized fun add(s: HistoryScrobble) = addRaw(s.artist, s.title, s.album.orEmpty(), 1, s.uts)

    private fun addRaw(artist: String, title: String, album: String, n: Int, uts: Long) {
        val v = map.getOrPut(normKey(Track(title, artist))) { HashMap() }.getOrPut(artist to title) { Variant(artist, title) }
        v.count += n
        if (uts > v.lastUts) v.lastUts = uts
        v.albums.merge(album, n, Int::plus)
        size += n
    }

    @Synchronized fun clear() { map.clear(); size = 0 }

    @Synchronized fun lookup(t: Track): HistoryMatch? {
        val v = map[normKey(t)]?.values?.maxWithOrNull(compareBy<Variant> { it.count }.thenBy { it.lastUts }) ?: return null
        val album = v.albums.filterKeys { it.isNotEmpty() }.entries.maxByOrNull { it.value }?.key
        return HistoryMatch(v.artist, v.title, album, v.count)
    }

    // TSV: artist, title, album, count, lastUts (una riga per variante+album). La chiave si ricalcola al caricamento.
    @Synchronized fun toTsv(): String = buildString {
        map.values.forEach { vs -> vs.values.forEach { v -> v.albums.forEach { (al, n) ->
            append(esc(v.artist)).append('\t').append(esc(v.title)).append('\t').append(esc(al)).append('\t')
                .append(n).append('\t').append(v.lastUts).append('\n')
        } } }
    }

    @Synchronized fun loadTsv(text: String) {
        clear()
        for (line in text.lineSequence()) {
            val c = line.split('\t')
            if (c.size != 5) continue
            val n = c[3].toIntOrNull() ?: continue
            addRaw(unesc(c[0]), unesc(c[1]), unesc(c[2]), n, c[4].toLongOrNull() ?: 0L)
        }
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")

    private fun unesc(s: String): String {
        if ('\\' !in s) return s
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length) {
                i++
                sb.append(when (s[i]) { 't' -> '\t'; 'n' -> '\n'; 'r' -> '\r'; else -> s[i] })
            } else sb.append(ch)
            i++
        }
        return sb.toString()
    }
}
