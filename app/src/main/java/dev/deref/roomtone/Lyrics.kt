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
        val q = URLEncoder.encode("${firstArtistRaw(t.artist)} ${searchTitle(t.title)}", "UTF-8")
        val body = try {
            httpGet("https://api.genius.com/search?q=$q", headers = mapOf("Authorization" to "Bearer $token"))?.toString(Charsets.UTF_8)
        } catch (_: Exception) { null } ?: return@withContext null // errore → niente cache
        val found = try {
            val hits = JSONObject(body).getJSONObject("response").getJSONArray("hits")
            val fk = firstArtist(t.artist); val tk = titleKey(t.title)
            (0 until hits.length()).map { hits.getJSONObject(it) }.filter { it.optString("type") == "song" }
                .mapNotNull { it.optJSONObject("result") }.firstOrNull { r ->
                    val a = base(r.optJSONObject("primary_artist")?.optString("name").orEmpty())
                    titleKey(r.optString("title")) == tk && (a.contains(fk) || fk.contains(a)) &&
                        (!r.has("lyrics_state") || r.optString("lyrics_state") == "complete")
                }?.optString("url")?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) { return@withContext null } // risposta illeggibile → niente cache
        synchronized(cache) { cache[key] = found }
        found
    }
}
