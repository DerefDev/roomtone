package dev.deref.roomtone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Stato per la UI: caricamento finché la coroutine non torna.
sealed interface LyricsState {
    data object Loading : LyricsState
    data class Found(val url: String) : LyricsState
    data object None : LyricsState
    data object Instrumental : LyricsState
}

// Link al testo su Genius. Mai loggare URL/header (token).
// ponytail: cache solo in memoria, nessuna scadenza → su disco come Durations se serve
object Lyrics {
    private val cache = HashMap<String, String?>() // chiave → url (null = non trovato)

    private val instr = HashMap<String, Boolean>() // normKey → strumentale (solo risposte valide)

    // Genius non espone "strumentale": tag Last.fm "instrumental" con almeno 10 voti
    private suspend fun instrumental(t: Track): Boolean = withContext(Dispatchers.IO) {
        val key = normKey(t)
        synchronized(instr) { instr[key] }?.let { return@withContext it }
        val k = BuildConfig.LASTFM_API_KEY
        if (k.isEmpty()) return@withContext false
        val a = URLEncoder.encode(firstArtistRaw(t.artist), "UTF-8"); val n = URLEncoder.encode(stripFeat(t.title), "UTF-8")
        try {
            val body = (URL("https://ws.audioscrobbler.com/2.0/?method=track.getTopTags&artist=$a&track=$n&autocorrect=1&api_key=$k&format=json").openConnection() as HttpURLConnection).run {
                connectTimeout = 10_000; readTimeout = 10_000
                setRequestProperty("User-Agent", MusicBrainz.UA)
                try { if (responseCode == 200) inputStream.bufferedReader().readText() else null } finally { disconnect() }
            } ?: return@withContext false
            val tags = JSONObject(body).optJSONObject("toptags")?.optJSONArray("tag")
            val r = (0 until (tags?.length() ?: 0)).map { tags!!.getJSONObject(it) }
                .any { it.optString("name").equals("instrumental", true) && it.optInt("count") >= 10 }
            synchronized(instr) { instr[key] = r }
            r
        } catch (_: Exception) { false } // errore → non strumentale, senza cache
    }

    suspend fun state(t: Track): LyricsState =
        if (instrumental(t)) LyricsState.Instrumental else url(t)?.let { LyricsState.Found(it) } ?: LyricsState.None

    suspend fun url(t: Track): String? = withContext(Dispatchers.IO) {
        val token = BuildConfig.GENIUS_ACCESS_TOKEN
        if (token.isEmpty()) return@withContext null
        val key = normKey(t)
        synchronized(cache) { if (cache.containsKey(key)) return@withContext cache[key] }
        val q = URLEncoder.encode("${firstArtistRaw(t.artist)} ${stripFeat(t.title)}", "UTF-8")
        val body = try {
            (URL("https://api.genius.com/search?q=$q").openConnection() as HttpURLConnection).run {
                connectTimeout = 10_000; readTimeout = 10_000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("User-Agent", MusicBrainz.UA)
                try { if (responseCode == 200) inputStream.bufferedReader().readText() else null } finally { disconnect() }
            }
        } catch (_: Exception) { null } ?: return@withContext null // errore → niente cache
        val found = try {
            val hits = JSONObject(body).getJSONObject("response").getJSONArray("hits")
            val fk = firstArtist(t.artist); val tk = normKey(Track(t.title, ""))
            (0 until hits.length()).map { hits.getJSONObject(it) }.filter { it.optString("type") == "song" }
                .mapNotNull { it.optJSONObject("result") }.firstOrNull { r ->
                    val a = base(r.optJSONObject("primary_artist")?.optString("name").orEmpty())
                    normKey(Track(r.optString("title"), "")) == tk && (a.contains(fk) || fk.contains(a)) &&
                        (!r.has("lyrics_state") || r.optString("lyrics_state") == "complete")
                }?.optString("url")?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) { return@withContext null } // risposta illeggibile → niente cache
        synchronized(cache) { cache[key] = found }
        found
    }
}
