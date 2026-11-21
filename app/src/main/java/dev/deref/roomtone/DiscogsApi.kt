package dev.deref.roomtone

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom

class DiscogsHttpException(val code: Int) : IOException("Discogs HTTP $code")

// Pulisce un nome artista Discogs: "Artista (2)" → "Artista", "Artista*" → "Artista"
private val dgNum = Regex("\\s*\\(\\d+\\)\\s*$")
internal fun cleanDiscogsArtist(s: String) = dgNum.replace(s.trim().trimEnd('*').trim(), "").trim()

// Titolo dei risultati master in search: "Artista - Titolo" → "Titolo" (parte dopo il primo " - ")
internal fun discogsTitle(s: String) = s.substringAfter(" - ", s).trim()

// OAuth 1.0a PLAINTEXT (su HTTPS) + chiamate. Errori HTTP/JSON → IOException/Exception (decide il chiamante).
// Mai loggare token, secret, header. Senza login le chiamate usano key/secret del consumer in header (non in URL).
object DiscogsApi {
    const val UA = "Roomtone/0.6 +https://github.com/DerefDev/roomtone"
    const val CALLBACK = "roomtone://discogs"
    private const val BASE = "https://api.discogs.com"
    private val lock = Mutex()
    private var last = 0L
    private val rnd = SecureRandom()
    private lateinit var secure: SecureStore
    private lateinit var account: SharedPreferences // discogs_user in chiaro

    @Synchronized fun init(ctx: Context) { // idempotente
        if (::secure.isInitialized) return
        val app = ctx.applicationContext
        secure = SecureStore(app)
        account = app.getSharedPreferences("discogs", Context.MODE_PRIVATE)
    }

    fun savedUser(): String? = account.getString("discogs_user", null)
    fun clear() {
        secure.remove("discogs_token"); secure.remove("discogs_token_secret"); secure.remove("discogs_req_secret")
        account.edit().clear().apply()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    // ponytail: PLAINTEXT → firma = secret&tokenSecret (valida solo su HTTPS) → HMAC-SHA1 se servisse HTTP
    private fun oauth(token: String?, tokenSecret: String?, extra: Map<String, String> = emptyMap()): String {
        val p = linkedMapOf(
            "oauth_consumer_key" to BuildConfig.DISCOGS_CONSUMER_KEY,
            "oauth_nonce" to ByteArray(12).also(rnd::nextBytes).joinToString("") { "%02x".format(it) },
            "oauth_signature" to enc(BuildConfig.DISCOGS_CONSUMER_SECRET) + "&" + enc(tokenSecret.orEmpty()),
            "oauth_signature_method" to "PLAINTEXT",
            "oauth_timestamp" to (System.currentTimeMillis() / 1000).toString(),
        )
        if (token != null) p["oauth_token"] = token
        p += extra
        // la firma contiene già "&" separatore e parti codificate → non ricodificare
        return "OAuth " + p.entries.joinToString(", ") { (k, v) -> "$k=\"${if (k == "oauth_signature") v else enc(v)}\"" }
    }

    private fun authHeader(): String {
        val t = secure.get("discogs_token"); val s = secure.get("discogs_token_secret")
        return if (t != null && s != null) oauth(t, s)
        else "Discogs key=${BuildConfig.DISCOGS_CONSUMER_KEY}, secret=${BuildConfig.DISCOGS_CONSUMER_SECRET}"
    }

    // 60 richieste/min: ≥ 1_050 ms tra richieste; 429 → attende 60 s e riprova una volta
    private suspend fun request(url: String, method: String, header: () -> String): String = lock.withLock {
        var retried = false
        while (true) {
            val wait = last + 1_050 - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            val (code, text) = withContext(Dispatchers.IO) {
                val c = URL(url).openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 10_000; c.readTimeout = 15_000
                    c.requestMethod = method
                    c.setRequestProperty("User-Agent", UA)
                    c.setRequestProperty("Authorization", header())
                    c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    if (method == "POST") { c.doOutput = true; c.outputStream.use { } }
                    val code = c.responseCode
                    code to (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
                } finally { c.disconnect() }
            }
            last = System.currentTimeMillis()
            if (code == 429 && !retried) { retried = true; delay(60_000); continue }
            if (code != 200) throw DiscogsHttpException(code)
            return@withLock text
        }
        @Suppress("UNREACHABLE_CODE") ""
    }

    suspend fun get(path: String, params: Map<String, String> = emptyMap()): JSONObject {
        val q = if (params.isEmpty()) "" else "?" + params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val text = request("$BASE$path$q", "GET") { authHeader() }
        return try { JSONObject(text) } catch (_: Exception) { throw IOException("Discogs: risposta illeggibile") }
    }

    private fun form(s: String) = s.split('&').mapNotNull { kv ->
        kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to URLDecoder.decode(it[1], "UTF-8") }
    }.toMap()

    // Passo 1: token temporaneo (il secret resta cifrato) → URL di autorizzazione per il browser
    suspend fun requestToken(): String {
        val r = form(request("$BASE/oauth/request_token", "GET") { oauth(null, null, mapOf("oauth_callback" to CALLBACK)) })
        val t = r["oauth_token"]; val s = r["oauth_token_secret"]
        if (t == null || s == null) throw IOException("Discogs: token mancante")
        secure.put("discogs_req_secret", s)
        return t
    }

    fun authorizeUrl(token: String) = "https://www.discogs.com/oauth/authorize?oauth_token=${enc(token)}"

    // Passo 2: callback con oauth_verifier → token definitivo (cifrato) → username
    suspend fun accessToken(token: String, verifier: String): String {
        val rs = secure.get("discogs_req_secret") ?: throw IOException("Discogs: richiesta scaduta")
        val r = form(request("$BASE/oauth/access_token", "POST") { oauth(token, rs, mapOf("oauth_verifier" to verifier)) })
        val t = r["oauth_token"]; val s = r["oauth_token_secret"]
        if (t == null || s == null) throw IOException("Discogs: token mancante")
        secure.put("discogs_token", t); secure.put("discogs_token_secret", s); secure.remove("discogs_req_secret")
        val name = get("/oauth/identity").getString("username")
        account.edit().putString("discogs_user", name).apply()
        return name
    }
}
