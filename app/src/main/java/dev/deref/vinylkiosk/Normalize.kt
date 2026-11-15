package dev.deref.vinylkiosk

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

private fun base(s: String) = accents.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")

private fun clean(s: String) = spaces.replace(punct.replace(s, " "), " ").trim()

// ponytail: euristica a regex → liste di parole chiave configurabili se servono altri casi
fun normKey(t: Track): String {
    // ponytail: remix/rmx/live non rimossi (versioni diverse)
    val title = clean(suffix.replace(brackets.replace(base(t.title), ""), ""))
    val artist = clean(artistSep.replace(featCut.replace(base(t.artist), ""), " "))
    return "$artist|$title"
}
