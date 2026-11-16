package dev.deref.roomtone

import java.text.Normalizer

// Kotlin puro. Chiave di uguaglianza tra versioni "equivalenti" dello stesso brano.
private val accents = Regex("\\p{Mn}+")
private val spaces = Regex("\\s+")
private val punct = Regex("[^\\p{L}\\p{N}\\s]")
private const val KW = "feat\\.?|ft\\.?|featuring|remaster(?:ed)?|deluxe|bonus|version"
private val brackets = Regex("\\s*[(\\[][^)\\]]*\\b(?:$KW)(?![\\p{L}\\p{N}])[^)\\]]*[)\\]]")
private val suffix = Regex("\\s-\\s[^-]*\\b(?:remaster(?:ed)?|deluxe|mono|stereo)\\b.*$")
private val featCut = Regex("\\s(?:feat|ft|featuring)\\b.*$")
private val artistSep = Regex("&|\\band\\b|,")

internal fun base(s: String) = accents.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")

private fun clean(s: String) = spaces.replace(punct.replace(s, " "), " ").trim()

// ponytail: euristica a regex → liste di parole chiave configurabili se servono altri casi
fun normKey(t: Track): String {
    // ponytail: remix/rmx/live non rimossi (versioni diverse)
    val title = clean(suffix.replace(brackets.replace(base(t.title), ""), ""))
    val artist = clean(artistSep.replace(featCut.replace(base(t.artist), ""), " "))
    return "$artist|$title"
}

private val featTitle = Regex("\\s*[(\\[][^)\\]]*\\b(?:feat|ft|featuring)\\b[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE)
private val firstSep = Regex("&|,|;|\\band\\b|\\bfeat\\b|\\bft\\b|\\bfeaturing\\b", RegexOption.IGNORE_CASE)

// Primo artista con maiuscole originali (per query verso API): "Artist One & Artist Two" → "Artist One"
fun firstArtistRaw(artist: String) = firstSep.split(artist).first().trim()

// Primo artista in forma base() (minuscolo, senza accenti): "Artist One & Artist Two" → "artist one"
fun firstArtist(artist: String) = base(firstArtistRaw(artist)).trim()

// "Song (feat. X)" → "Song"
fun stripFeat(title: String) = featTitle.replace(title, "").trim()

private val featGuests = Regex("[(\\[]\\s*(?:feat|ft|featuring)\\b\\.?\\s*([^)\\]]+)[)\\]]", RegexOption.IGNORE_CASE)

// Solo a schermo: "Song (feat. X & Y)" di "A, B & C" → "Song" / "A, B, C feat. X, Y"
// ponytail: gruppo = isGroup (Discogs/MusicBrainz, vedi Artists) → finché non risolto il nome si spezza
private fun guests(t: Track) = featGuests.find(t.title)?.groupValues?.get(1)?.trim().orEmpty()
private fun parts(s: String) = s.split(", ").filter { " & " in it }

fun displayTitle(t: Track) = stripFeat(t.title)

// Parti con " & " (artisti + ospiti) da verificare come nomi di gruppo
fun ampParts(t: Track): List<String> = (parts(t.artist) + parts(guests(t))).distinct()

fun displayArtist(t: Track, isGroup: (String) -> Boolean = { false }): String {
    fun fix(s: String) = s.split(", ").joinToString(", ") { if (" & " in it && !isGroup(it)) it.replace(" & ", ", ") else it }
    val a = fix(t.artist)
    val f = fix(guests(t))
    return if (f.isEmpty()) a else "$a feat. $f"
}
