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

// Chiave di un solo titolo (senza artista)
fun titleKey(s: String) = normKey(Track(s, ""))

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

private val bracketsCI = Regex(brackets.pattern, RegexOption.IGNORE_CASE)
private val suffixCI = Regex(suffix.pattern, RegexOption.IGNORE_CASE)

// Titolo per le ricerche testuali (Genius): senza feat./remaster/deluxe, maiuscole originali.
// "Song (2025 remaster)", "Song - 2025 Remaster", "Song (feat. X)" → "Song"
fun searchTitle(title: String) = suffixCI.replace(bracketsCI.replace(title, ""), "").trim()

private val anyBrackets = Regex("\\s*[(\\[][^)\\]]*[)\\]]")
private val inlineFeat = Regex("\\s(?:feat|ft|featuring)\\.?\\s.*$", RegexOption.IGNORE_CASE)

// Titolo "di base" per i testi: prima di " - ", senza parentesi e senza " ft. X".
// "Song - Acoustic Version", "Song (con X)", "Song ft. X" → "Song". ponytail: unisce anche intro/live allo stesso testo
fun coreTitle(title: String) = inlineFeat.replace(anyBrackets.replace(title.substringBefore(" - "), ""), "").trim()

// Solo a schermo: "Song (feat. X & Y)" di "A, B & C" → "Song" / "A, B, C feat. X, Y"
// ponytail: gruppo = isGroup (Discogs/MusicBrainz, vedi Artists) → finché non risolto il nome si spezza
private fun parts(s: String) = s.split(", ").filter { " & " in it }

// Parti con " & " (artisti + ospiti) da verificare come nomi di gruppo
fun ampParts(t: Track): List<String> = (parts(t.artist) + parts(featGuests(t.title).orEmpty())).distinct()

fun displayArtist(t: Track, isGroup: (String) -> Boolean = { false }): String {
    fun fix(s: String) = s.split(", ").joinToString(", ") { if (" & " in it && !isGroup(it)) it.replace(" & ", ", ") else it }
    val a = fix(t.artist)
    val f = fix(featGuests(t.title).orEmpty())
    return if (f.isEmpty()) a else "$a feat. $f"
}

// Chiave della cronologia: solo il primo artista + titolo normalizzato.
// ponytail: i duo condividono la chiave con i brani solisti del primo membro con lo stesso titolo → accettato, l'utente può scegliere
fun histKey(t: Track) = normKey(Track(t.title, firstArtistRaw(t.artist)))

// Somiglianza tra titoli (0..1) sulle titleKey: 1.0 se uguali, altrimenti max(Jaccard sui token, 1 - Levenshtein/lunghezza max). "part" ≡ "pt".
private fun tkey(s: String) = titleKey(s).substringAfter('|')
private fun tokens(k: String) = k.split(' ').filter { it.isNotEmpty() }.map { if (it == "part") "pt" else it }.toSet()

private fun levenshtein(a: String, b: String): Int {
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1).also { it[0] = i }
        for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        prev = cur
    }
    return prev[b.length]
}

fun titleSim(a: String, b: String): Double {
    val ka = tkey(a); val kb = tkey(b)
    if (ka == kb) return 1.0
    if (ka.replace(" ", "") == kb.replace(" ", "")) return 0.99 // sigle: "S.I.C." ≈ "Sic"
    if (digits.findAll(ka).map { it.value }.toList() != digits.findAll(kb).map { it.value }.toList()) return 0.0 // "Pt 1" ≠ "Pt 2"
    val ta = tokens(ka); val tb = tokens(kb)
    val jac = if (ta.isEmpty() || tb.isEmpty()) 0.0 else ta.intersect(tb).size.toDouble() / ta.union(tb).size
    val m = maxOf(ka.length, kb.length)
    return maxOf(jac, 1.0 - levenshtein(ka, kb).toDouble() / m)
}

private val digits = Regex("\\d+")

const val TITLE_MIN = 0.85

// Indice del candidato: chiave esatta, altrimenti l'unico migliore con titleSim ≥ 0.85 (pareggio → null, ambiguo)
fun bestTitle(title: String, candidates: List<String>): Int? {
    val k = tkey(title)
    candidates.indexOfFirst { tkey(it) == k }.takeIf { it >= 0 }?.let { return it }
    val s = candidates.map { titleSim(title, it) }
    val best = s.maxOrNull() ?: return null
    return if (best >= TITLE_MIN && s.count { it == best } == 1) s.indexOf(best) else null
}
