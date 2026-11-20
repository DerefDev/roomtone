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
    suspend fun state(t: Track): LyricsState = url(t)?.let { LyricsState.Found(it) } ?: LyricsState.None

    suspend fun url(t: Track): String? = withContext(Dispatchers.IO) {
        val token = BuildConfig.GENIUS_ACCESS_TOKEN
        if (token.isEmpty()) return@withContext null
        val key = normKey(t)
        synchronized(cache) { if (cache.containsKey(key)) return@withContext cache[key] }
        val fa = firstArtistRaw(t.artist); val fk = firstArtist(t.artist)
        val st = searchTitle(t.title); val core = coreTitle(t.title)
        // 1ª ricerca: titolo senza feat./remaster, confronto esatto; poi titolo di base (prima di " - ", senza parentesi)
        val hits = search(token, "$fa $st") ?: return@withContext null // errore → niente cache
        fun ok(r: JSONObject) = artistOk(r, fk) && (!r.has("lyrics_state") || r.optString("lyrics_state") == "complete")
        val tk = titleKey(t.title); val ck = titleKey(core)
        fun byCore(l: List<JSONObject>) = l.firstOrNull { core.isNotEmpty() && titleKey(coreTitle(it.optString("title"))) == ck && ok(it) }
        val hit = hits.firstOrNull { titleKey(it.optString("title")) == tk && ok(it) } ?: byCore(hits)
            ?: if (core.isNotEmpty() && core != st) byCore(search(token, "$fa $core") ?: return@withContext null) else null
        val found = hit?.optString("url")?.takeIf { it.isNotEmpty() }
        synchronized(cache) { cache[key] = found }
        found
    }

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
