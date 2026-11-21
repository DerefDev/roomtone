package dev.deref.roomtone

import java.net.HttpURLConnection
import java.net.URL

// JSON null → null (optString di Android restituirebbe la stringa "null"); vuota → null
fun org.json.JSONObject.optStr(k: String): String? = if (isNull(k)) null else optString(k).takeIf { it.isNotEmpty() }

// GET minimale: null = risposta non 200; errori di rete → IOException. Mai loggare l'URL (può contenere chiavi).
fun httpGet(url: String, ua: String = MusicBrainz.UA, headers: Map<String, String> = emptyMap()): ByteArray? =
    (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = 10_000; readTimeout = 10_000
        setRequestProperty("User-Agent", ua)
        headers.forEach { (k, v) -> setRequestProperty(k, v) }
        try { if (responseCode == 200) inputStream.use { it.readBytes() } else null } finally { disconnect() }
    }
