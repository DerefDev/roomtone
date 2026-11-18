package dev.deref.roomtone

// Pulizia stile Pano Scrobbler dei metadati Discogs prima dello scrobble (modalità "owned"). Kotlin puro.
data class EditOptions(
    val remaster: Boolean = true, val versions: Boolean = false, val live: Boolean = false, val explicit: Boolean = true,
    val introOutro: Boolean = false, val featToArtist: Boolean = false, val albumEditions: Boolean = true,
    val mainArtistOnly: Boolean = false,
)

private val I = RegexOption.IGNORE_CASE
// Gruppo tra parentesi tonde/quadre che contiene la parola chiave; suffisso " - …" fino a fine stringa.
private fun paren(kw: String) = Regex("\\s*[(\\[][^)\\]]*(?:$kw)[^)\\]]*[)\\]]", I)
private fun parenExact(kw: String) = Regex("\\s*[(\\[]\\s*(?:$kw)\\s*[)\\]]", I)
private fun dash(kw: String) = Regex("\\s+[-–]\\s+[^-–]*(?:$kw)[^-–]*$", I)
private fun dashExact(kw: String) = Regex("\\s+[-–]\\s+(?:$kw)\\s*$", I)

private const val REMASTER = "\\bremaster(?:ed)?\\b"
private const val VERSIONS = "radio edit|single version|album version|mono|stereo|edit"
private const val EXPLICIT = "explicit|clean"
private const val INTRO = "intro|outro|skit"
private val remasterRx = listOf(paren(REMASTER), dash(REMASTER))
private val versionsRx = listOf(parenExact("(?:$VERSIONS)(?: version)?"), dashExact("(?:$VERSIONS)(?: version)?"))
private val liveRx = listOf(Regex("\\s*[(\\[]\\s*live\\b[^)\\]]*[)\\]]", I), Regex("\\s+[-–]\\s+live\\b.*$", I))
private val explicitRx = listOf(parenExact("(?:$EXPLICIT)(?: version)?"))
private val introRx = listOf(parenExact(INTRO), dashExact(INTRO))
private val featRx = Regex("\\s*[(\\[]\\s*(?:feat|ft|featuring)\\b\\.?\\s*([^)\\]]*)[)\\]]", I)
private val albumRx = listOf(
    paren("deluxe|expanded|remaster(?:ed)?|anniversary|special edition|bonus track"),
    dash("deluxe|expanded|remaster(?:ed)?|anniversary edition|special edition|bonus track"),
)
private val trailing = Regex("[\\s\\-–]+$")

private fun strip(s: String, rx: List<Regex>): String {
    var r = s
    for (x in rx) r = x.replace(r, "")
    return r.replace(trailing, "").trim()
}

// Ospiti "(feat. X)" del titolo, null se assenti.
fun featGuests(title: String): String? = featRx.find(title)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

fun cleanTitle(s: String, o: EditOptions): String {
    var r = s
    // ponytail: un passaggio per opzione, in ordine fisso → ripetere finché stabile se serve annidare più suffissi
    if (o.remaster) r = strip(r, remasterRx)
    if (o.versions) r = strip(r, versionsRx)
    if (o.live) r = strip(r, liveRx)
    if (o.explicit) r = strip(r, explicitRx)
    if (o.introOutro) strip(r, introRx).takeIf { it.isNotEmpty() }?.let { r = it } // mai titolo vuoto
    if (o.featToArtist) r = featRx.replace(r, "").trim()
    return r.ifEmpty { s }
}

fun cleanAlbum(s: String, o: EditOptions): String =
    if (o.albumEditions) strip(s, albumRx).ifEmpty { s } else s

// Artista pulito; con featToArtist aggiunge gli ospiti presi dal titolo originale (rawTitle).
fun cleanArtist(s: String, o: EditOptions, rawTitle: String = ""): String {
    val a = if (o.mainArtistOnly) firstArtistRaw(s).ifEmpty { s } else s
    val g = if (o.featToArtist) featGuests(rawTitle) else null
    return if (g != null) "$a feat. $g" else a
}
