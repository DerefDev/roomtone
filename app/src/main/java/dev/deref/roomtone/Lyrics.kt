package dev.deref.roomtone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

// Stato per la UI: caricamento finché la coroutine non torna.
sealed interface LyricsState {
    data object Loading : LyricsState
    data class Found(val url: String) : LyricsState
    data object None : LyricsState
}

// Link al testo su Genius. Mai loggare URL/header (token).
// ponytail: cache solo in memoria, nessuna scadenza → su disco come Durations se serve
object Lyrics {
    private val cache = HashMap<String, String?>() // chiave → url (null = non trovato)

    // Genius non espone "strumentale" nella API (campo sempre null) e i tag Last.fm sono incoerenti → sempre il link
    suspend fun state(t: Track, ownedTitle: String? = null, album: String? = null): LyricsState =
        url(t, ownedTitle, album)?.let { LyricsState.Found(it) } ?: LyricsState.None

    private val generic = setOf("intro", "outro", "interlude", "skit")

    suspend fun url(t: Track, ownedTitle: String? = null, album: String? = null): String? = withContext(Dispatchers.IO) {
        val token = BuildConfig.GENIUS_ACCESS_TOKEN
        if (token.isEmpty()) return@withContext null
        val key = normKey(t) + "|" + ownedTitle?.let(::titleKey).orEmpty()
        synchronized(cache) { if (cache.containsKey(key)) return@withContext cache[key] }
        var c = candidates(token, t) ?: return@withContext null // errore → niente cache
        // titolo esatto sul disco posseduto diverso da quello di Shazam → stessi passaggi con quello
        if (c.isEmpty() && ownedTitle != null && titleKey(ownedTitle) != titleKey(t.title)) c = candidates(token, Track(ownedTitle, t.artist)) ?: return@withContext null
        var hit = c.firstOrNull()
        // titoli generici (intro, outro…): più brani con lo stesso nome → quello dell'album giusto
        if (album != null && c.size >= 2 && titleKey(coreTitle(ownedTitle ?: t.title)) in generic) {
            val ak = titleKey(album)
            hit = c.take(3).firstOrNull { albumName(token, it.optLong("id"))?.let(::titleKey) == ak } ?: hit
        }
        val found = hit?.optString("url")?.takeIf { it.isNotEmpty() }
        synchronized(cache) { cache[key] = found }
        found
    }

    // Risultati compatibili, in ordine di preferenza; null = errore di rete
    private fun candidates(token: String, t: Track): List<JSONObject>? {
        val fa = firstArtistRaw(t.artist); val fk = firstArtist(t.artist)
        val st = searchTitle(t.title); val core = coreTitle(t.title)
        // 1ª ricerca: titolo senza feat./remaster, confronto esatto; poi titolo di base (prima di " - ", senza parentesi)
        val hits = search(token, "$fa $st") ?: return null
        fun ok(r: JSONObject) = artistOk(r, fk) && (!r.has("lyrics_state") || r.optString("lyrics_state") == "complete")
        val tk = titleKey(t.title); val ck = titleKey(core)
        fun byCore(l: List<JSONObject>) = l.filter { core.isNotEmpty() && titleKey(coreTitle(it.optString("title"))) == ck && ok(it) }
        return hits.filter { titleKey(it.optString("title")) == tk && ok(it) }.ifEmpty { byCore(hits) }
            .ifEmpty { if (core.isNotEmpty() && core != st) byCore(search(token, "$fa $core") ?: return null) else emptyList() }
    }

    // Nome dell'album del brano da /songs/{id}; null = errore o assente
    private fun albumName(token: String, id: Long): String? = try {
        val body = httpGet("https://api.genius.com/songs/$id", headers = mapOf("Authorization" to "Bearer $token"))?.toString(Charsets.UTF_8)
        JSONObject(body ?: "").getJSONObject("response").getJSONObject("song").optJSONObject("album")?.optString("name")?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) { null }

    // Risultati di tipo "song"; null = errore di rete o risposta illeggibile
    private fun search(token: String, q: String): List<JSONObject>? = try {
        val body = httpGet("https://api.genius.com/search?q=${URLEncoder.encode(q, "UTF-8")}", headers = mapOf("Authorization" to "Bearer $token"))
            ?.toString(Charsets.UTF_8) ?: throw Exception()
        val hits = JSONObject(body).getJSONObject("response").getJSONArray("hits")
        (0 until hits.length()).map { hits.getJSONObject(it) }.filter { it.optString("type") == "song" }.mapNotNull { it.optJSONObject("result") }
    } catch (_: Exception) { null }

    // Artista principale compatibile, oppure presente nell'elenco completo degli artisti del brano
    private fun artistOk(r: JSONObject, fk: String): Boolean {
        val a = base(r.optJSONObject("primary_artist")?.optString("name").orEmpty())
        return a.contains(fk) || fk.contains(a) || base(r.optString("artist_names")).contains(fk)
    }
}
