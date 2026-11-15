package dev.deref.roomtone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class HistoryScrobble(val artist: String, val title: String, val album: String?, val uts: Long)

// Errori HTTP/JSON → eccezione (decide il chiamante). Mai loggare URL/parametri (token, session key).
object LastFmApi {
    private const val UA = "Roomtone/0.4 ( https://github.com/DerefDev/roomtone )"
    private const val BASE = "https://ws.audioscrobbler.com/2.0/"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun authUrl() = "https://www.last.fm/api/auth/?api_key=${enc(BuildConfig.LASTFM_API_KEY)}&cb=${enc("roomtone://auth")}"

    // ponytail: tutto in POST form → niente parametri negli URL
    private suspend fun call(params: Map<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val body = (params + ("format" to "json")).entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val c = URL(BASE).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000; c.readTimeout = 10_000
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            val o = try { JSONObject(text) } catch (_: Exception) { throw Exception("HTTP $code") }
            if (o.has("error")) throw Exception(o.optString("message", "Last.fm error ${o.optInt("error")}"))
            if (code != 200) throw Exception("HTTP $code")
            o
        } finally { c.disconnect() }
    }

    suspend fun getSession(token: String): Pair<String, String> {
        val p = mapOf("method" to "auth.getSession", "api_key" to BuildConfig.LASTFM_API_KEY, "token" to token)
        val s = call(p + ("api_sig" to sign(p, BuildConfig.LASTFM_API_SECRET))).getJSONObject("session")
        return s.getString("name") to s.getString("key")
    }

    suspend fun recentTracksPage(user: String, page: Int, from: Long?, to: Long?): Pair<List<HistoryScrobble>, Int> {
        val p = mutableMapOf(
            "method" to "user.getRecentTracks", "api_key" to BuildConfig.LASTFM_API_KEY,
            "user" to user, "limit" to "200", "page" to page.toString(),
        )
        from?.let { p["from"] = it.toString() }
        to?.let { p["to"] = it.toString() }
        val rt = call(p).getJSONObject("recenttracks")
        val pages = rt.optJSONObject("@attr")?.optString("totalPages")?.toIntOrNull() ?: 0
        // con una sola traccia Last.fm restituisce un oggetto invece di un array
        val arr = rt.optJSONArray("track")
        val items = arr?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            ?: rt.optJSONObject("track")?.let { listOf(it) } ?: emptyList()
        val out = items.mapNotNull { t ->
            if (t.optJSONObject("@attr")?.optString("nowplaying") == "true") return@mapNotNull null
            val uts = t.optJSONObject("date")?.optString("uts")?.toLongOrNull() ?: return@mapNotNull null
            HistoryScrobble(
                t.optJSONObject("artist")?.optString("#text").orEmpty(),
                t.optString("name"),
                t.optJSONObject("album")?.optString("#text")?.takeIf { it.isNotEmpty() },
                uts,
            )
        }
        return out to pages
    }
}
