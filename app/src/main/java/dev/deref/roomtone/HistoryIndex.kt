package dev.deref.roomtone

// Kotlin puro. Cronologia Last.fm aggregata per histKey → variante esatta (artista, titolo, album).
enum class MetaSource { HISTORY, SHAZAM, OWNED, DISCOGS }

// Metadati da inviare (v0.5) per un ascolto.
data class ScrobbleMeta(val artist: String, val title: String, val album: String?, val source: MetaSource, val albumSource: AlbumSource? = null)

fun scrobbleMeta(t: Track, v: Version?) =
    if (v != null) ScrobbleMeta(v.artist, v.title, v.album, MetaSource.HISTORY)
    else ScrobbleMeta(t.artist, t.title, null, MetaSource.SHAZAM)

// Variante come scritta in cronologia; recent = ultimi (max 5) uts, dal più recente.
data class Version(val artist: String, val title: String, val album: String?, val plays: Int, val recent: List<Long>) {
    val lastUts get() = recent.firstOrNull() ?: 0L
    val key get() = VersionKey(artist, title, album)
}
data class VersionKey(val artist: String, val title: String, val album: String?)

sealed interface Pick {
    data class Auto(val v: Version) : Pick
    data class Ask(val options: List<Version>) : Pick
}

private const val RECENT = 5

// Unisce due elenchi di versioni (senza doppioni per chiave), più ascoltate prima, a parità la più recente
fun mergeVersions(a: List<Version>, b: List<Version>): List<Version> =
    (a + b).distinctBy { it.key }.sortedWith(compareByDescending<Version> { it.plays }.thenByDescending { it.lastUts })

fun pick(vs: List<Version>, saved: VersionKey?): Pick? {
    if (saved != null) return Pick.Auto(vs.firstOrNull { it.key == saved } ?: Version(saved.artist, saved.title, saved.album, 0, emptyList()))
    if (vs.isEmpty()) return null
    if (vs.size == 1) return Pick.Auto(vs[0])
    val last = vs.flatMap { v -> v.recent.map { it to v } }.sortedByDescending { it.first }.take(RECENT)
    if (last.size >= RECENT && last.all { it.second === last[0].second }) return Pick.Auto(last[0].second)
    return Pick.Ask(vs)
}

class HistoryIndex {
    private class Var(val artist: String, val title: String, val album: String) {
        var count = 0
        var recent = emptyList<Long>()
    }

    private val map = HashMap<String, HashMap<Triple<String, String, String>, Var>>()
    var size = 0
        private set

    @Synchronized fun add(s: HistoryScrobble) = addRaw(s.artist, s.title, s.album.orEmpty(), 1, listOf(s.uts))

    private fun addRaw(artist: String, title: String, album: String, n: Int, uts: List<Long>) {
        val v = map.getOrPut(histKey(Track(title, artist))) { HashMap() }.getOrPut(Triple(artist, title, album)) { Var(artist, title, album) }
        v.count += n
        v.recent = (v.recent + uts).sortedDescending().take(RECENT)
        size += n
    }

    @Synchronized fun clear() { map.clear(); size = 0 }

    @Synchronized fun versions(t: Track): List<Version> =
        map[histKey(t)]?.values?.map { Version(it.artist, it.title, it.album.ifEmpty { null }, it.count, it.recent) }
            ?.sortedWith(compareByDescending<Version> { it.plays }.thenByDescending { it.lastUts }).orEmpty()

    // TSV v2: artist, title, album, count, recent (uts separati da virgola). La chiave si ricalcola al caricamento.
    @Synchronized fun toTsv(): String = buildString {
        map.values.forEach { vs -> vs.values.forEach { v ->
            append(esc(v.artist)).append('\t').append(esc(v.title)).append('\t').append(esc(v.album)).append('\t')
                .append(v.count).append('\t').append(v.recent.joinToString(",")).append('\n')
        } }
    }

    @Synchronized fun loadTsv(text: String) {
        clear()
        for (line in text.lineSequence()) {
            val c = line.split('\t')
            if (c.size != 5) continue
            val n = c[3].toIntOrNull() ?: continue
            val uts = if (c[4].isEmpty()) emptyList() else c[4].split(',').mapNotNull { it.toLongOrNull() }
            addRaw(unesc(c[0]), unesc(c[1]), unesc(c[2]), n, uts)
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
