package dev.deref.roomtone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ListenBrainzException(val code: Int, message: String) : Exception(message)

private const val LB_COUNT = 1000

// Pagina di GET user/{user}/listens → scrobble (dal più recente). MBID: mbid_mapping se c'è, altrimenti additional_info. Kotlin puro (org.json).
internal fun parseListens(json: JSONObject): List<HistoryScrobble> {
    val a = json.getJSONObject("payload").optJSONArray("listens") ?: return emptyList()
    return (0 until a.length()).mapNotNull { i ->
        val l = a.getJSONObject(i)
        val m = l.optJSONObject("track_metadata") ?: return@mapNotNull null
        val artist = m.optString("artist_name"); val title = m.optString("track_name")
        if (artist.isEmpty() || title.isEmpty() || !l.has("listened_at") || l.isNull("listened_at")) return@mapNotNull null
        fun mb(o: JSONObject?) = o?.let { j ->
            Mbids(j.optStr("recording_mbid"), j.optStr("release_mbid"), j.optJSONArray("artist_mbids")?.let { x -> List(x.length()) { x.getString(it) } }.orEmpty())
        }?.takeIf { !it.empty }
        HistoryScrobble(artist, title, m.optStr("release_name"), l.getLong("listened_at"),
            mb(m.optJSONObject("mbid_mapping")) ?: mb(m.optJSONObject("additional_info")))
    }
}

// Corpo di POST submit-listens (import). Mai mbid_mapping. durationSec → duration_ms.
internal fun submitPayload(items: List<QItem>, version: String = BuildConfig.VERSION_NAME): String {
    val p = JSONArray()
    items.forEach { q ->
        val info = JSONObject().put("submission_client", "Roomtone").put("submission_client_version", version).put("media_player", "Roomtone")
        q.durationSec?.let { info.put("duration_ms", it * 1000) }
        q.mbids?.let { m ->
            m.recording?.let { info.put("recording_mbid", it) }
            m.release?.let { info.put("release_mbid", it) }
            if (m.artists.isNotEmpty()) info.put("artist_mbids", JSONArray(m.artists))
        }
        val tm = JSONObject().put("artist_name", q.artist).put("track_name", q.title)
        q.album?.let { tm.put("release_name", it) }
        p.put(JSONObject().put("listened_at", q.ts).put("track_metadata", tm.put("additional_info", info)))
    }
    return JSONObject().put("listen_type", "import").put("payload", p).toString()
}

// Errori HTTP/JSON → eccezione (decide il chiamante). Mai loggare il token. Max 1 richiesta/s, rispetta 429 e X-RateLimit-*.
object ListenBrainzApi : HistoryBackend {
    private val UA = "Roomtone/${BuildConfig.VERSION_NAME} ( https://github.com/DerefDev/roomtone )"
    private const val BASE = "https://api.listenbrainz.org/1/"

    private val gate = Mutex()
    private var nextAt = 0L

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    // Una richiesta alla volta, almeno 1 s dalla precedente (o fino al reset indicato dal server)
    private suspend fun <T> throttled(f: suspend () -> T): T = gate.withLock {
        val w = nextAt - System.currentTimeMillis()
        if (w > 0) delay(w)
        nextAt = System.currentTimeMillis() + 1000
        f()
    }

    private suspend fun call(path: String, token: String? = null, body: String? = null): JSONObject {
        repeat(2) { attempt ->
            val (code, text) = throttled {
                withContext(Dispatchers.IO) {
                    val c = URL(BASE + path).openConnection() as HttpURLConnection
                    try {
                        c.connectTimeout = 10_000; c.readTimeout = 20_000
                        c.setRequestProperty("User-Agent", UA)
                        token?.let { c.setRequestProperty("Authorization", "Token $it") }
                        if (body != null) {
                            c.requestMethod = "POST"; c.doOutput = true
                            c.setRequestProperty("Content-Type", "application/json")
                            c.outputStream.use { it.write(body.toByteArray()) }
                        }
                        val code = c.responseCode
                        val reset = c.getHeaderField("X-RateLimit-Reset-In")?.toLongOrNull()
                        if (code == 429 || c.getHeaderField("X-RateLimit-Remaining")?.toIntOrNull() == 0)
                            nextAt = maxOf(nextAt, System.currentTimeMillis() + (reset ?: 1) * 1000)
                        code to (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
                    } finally { c.disconnect() }
                }
            }
            if (code == 429) { if (attempt == 0) return@repeat; throw IOException("HTTP 429") } // dopo l'attesa del reset, un solo nuovo tentativo
            if (code >= 500) throw IOException("HTTP $code")
            // 2xx non JSON = pagina anti-bot HTML (succede alle richieste anonime): errore temporaneo, mai successo
            if (code in 200..299) return try { JSONObject(text.ifBlank { "{}" }) } catch (_: Exception) { throw IOException("non-JSON response") }
            val msg = try { JSONObject(text).optString("error") } catch (_: Exception) { "" }.ifEmpty { "HTTP $code" }
            throw ListenBrainzException(code, msg.take(120))
        }
        throw IOException("HTTP 429")
    }

    // Verifica il token personale → nome utente. Token non valido → eccezione.
    suspend fun validate(token: String): String {
        val o = call("validate-token", token)
        if (!o.optBoolean("valid")) throw ListenBrainzException(401, "invalid token")
        return o.getString("user_name")
    }

    suspend fun submit(payload: String, token: String) { call("submit-listens", token, payload) }

    // letture col token anche se pubbliche: alle richieste anonime ListenBrainz può rispondere con la verifica anti-bot
    private fun token() = Services.listenbrainz.history.secret()

    suspend fun listens(user: String, minTs: Long, maxTs: Long): List<HistoryScrobble> =
        parseListens(call("user/${enc(user)}/listens?min_ts=$minTs&max_ts=$maxTs&count=$LB_COUNT", token()))

    // Cursore = max_ts (escluso), nullo → fino a `to`. Si pagina sempre all'indietro; `from` = stop dell'incrementale.
    override suspend fun page(user: String, cursor: Long?, from: Long?, to: Long?): HistoryPage {
        val maxTs = cursor ?: ((to ?: (System.currentTimeMillis() / 1000)) + 1)
        val all = parseListens(call("user/${enc(user)}/listens?max_ts=$maxTs&count=$LB_COUNT", token()))
        val items = if (from != null) all.filter { it.uts >= from } else all
        if (items.size < all.size || all.size < LB_COUNT) return HistoryPage(items, null, null) // raggiunto `from` / ultima pagina (ponytail: meno di 1000 = fine, niente pagina vuota in più)
        val min = all.minOf { it.uts }
        // pagina piena: i listen con lo stesso secondo del minimo potrebbero continuare nella pagina dopo → si scartano e si riprendono
        // da min inclusivo (max_ts = min + 1). Se tutta la pagina ha lo stesso secondo → si prosegue da min escluso (ponytail: si perde il resto di quel secondo)
        val kept = items.filter { it.uts > min }
        return if (kept.isEmpty()) HistoryPage(items, min, null) else HistoryPage(kept, min + 1, null)
    }
}

// Un 400 respinge l'intero blocco: si divide a metà ricorsivamente e solo gli elementi che danno 400 da soli sono ignorati.
// Altro errore a metà: se qualcosa è già stato accettato non si perde (i restanti → Retry, l'errore riemerge al giro dopo);
// se niente è stato accettato l'errore si propaga com'è (la coda lo gestisce: sessione scaduta, backoff, recover).
internal suspend fun sendSplitting(items: List<QItem>, submit: suspend (List<QItem>) -> Unit): List<SendResult> {
    val out = ArrayList<SendResult>()
    suspend fun go(b: List<QItem>) {
        try {
            submit(b)
            b.forEach { _ -> out += SendResult.Ok }
        } catch (e: ListenBrainzException) {
            if (e.code != 400) throw e
            if (b.size == 1) out += SendResult.Ignored(e.message.orEmpty())
            else { go(b.take(b.size / 2)); go(b.drop(b.size / 2)) }
        }
    }
    try { go(items) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
        if (out.none { it == SendResult.Ok }) throw e
        while (out.size < items.size) out += SendResult.Retry
    }
    return out
}

object ListenBrainzScrobbler : Scrobbler {
    override val batchSize = 100
    override suspend fun send(items: List<QItem>, secret: String) =
        sendSplitting(items) { ListenBrainzApi.submit(submitPayload(it), secret) }
    override fun sessionExpired(e: Exception) = (e as? ListenBrainzException)?.code == 401
    // min_ts/max_ts sono esclusi: la finestra ±1 s di recover() li rende di fatto inclusivi
    // pagina all'indietro finché non copre la finestra; ponytail: più di 1000 listen nello stesso secondo → il resto si perde
    override suspend fun seen(user: String, from: Long, to: Long): Set<Long> {
        val seen = HashSet<Long>()
        var max = to
        while (true) {
            val page = ListenBrainzApi.listens(user, from, max)
            page.mapTo(seen) { it.uts }
            if (page.size < LB_COUNT) return seen
            val min = page.minOf { it.uts }
            if (min >= max) return seen
            max = min
        }
    }
}
