package dev.deref.roomtone

import java.net.HttpURLConnection
import java.net.URL

// GET minimale: null = risposta non 200; errori di rete → IOException. Mai loggare l'URL (può contenere chiavi).
fun httpGet(url: String, ua: String = MusicBrainz.UA): ByteArray? =
    (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = 10_000; readTimeout = 10_000
        setRequestProperty("User-Agent", ua)
        try { if (responseCode == 200) inputStream.use { it.readBytes() } else null } finally { disconnect() }
    }
