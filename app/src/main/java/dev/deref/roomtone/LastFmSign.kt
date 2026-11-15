package dev.deref.roomtone

import java.security.MessageDigest

// Kotlin puro. Firma Last.fm: md5(nome+valore ordinati per nome, escluso format/callback, + secret).
fun sign(params: Map<String, String>, secret: String): String {
    val s = params.filterKeys { it != "format" && it != "callback" }.toSortedMap()
        .entries.joinToString("") { it.key + it.value } + secret
    return MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
