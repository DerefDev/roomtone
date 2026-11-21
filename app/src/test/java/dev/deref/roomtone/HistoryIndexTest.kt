package dev.deref.roomtone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryIndexTest {
    private fun HistoryIndex.add(title: String, album: String?, n: Int, uts: Long = 1, artist: String = "Artist A") =
        repeat(n) { add(HistoryScrobble(artist, title, album, uts)) }

    private fun sample() = HistoryIndex().apply {
        add("Song A - 2025 Remaster", "Compilation", 4)
        add("Song A", "Original", 2)
        add("Song A", null, 1)
    }

    private val t = Track("Song A", "Artist A")

    @Test fun versioniOrdinatePerAscolti() {
        val vs = sample().versions(t)
        assertEquals(listOf(4, 2, 1), vs.map { it.plays })
        assertEquals("Compilation", vs[0].album)
        assertEquals(null, vs[2].album)
    }
    @Test fun parita_vinceUtsRecente() {
        val h = HistoryIndex().apply { add("Song A", "Old", 2, 100); add("Song A", "New", 2, 200) }
        assertEquals("New", h.versions(t)[0].album)
    }
    @Test fun recentMax5Decrescente() {
        val h = HistoryIndex().apply { (1L..8L).forEach { add(HistoryScrobble("Artist A", "Song A", "X", it)) } }
        assertEquals(listOf(8L, 7L, 6L, 5L, 4L), h.versions(t)[0].recent)
        assertEquals(8, h.versions(t)[0].plays)
    }
    @Test fun assente() = assertTrue(sample().versions(Track("Other", "Artist A")).isEmpty())
    @Test fun primoArtistaStessaChiave() {
        val h = sample()
        assertEquals(h.versions(t), h.versions(Track("Song A", "Artist A & Guest B")))
    }
    @Test fun tsvRoundTrip() {
        val h = sample().apply { add(HistoryScrobble("A\tB", "T\nx\\y", "Al", 5)) }
        val h2 = HistoryIndex().apply { loadTsv(h.toTsv() + "riga rotta\n1\t2\n") }
        assertEquals(h.size, h2.size)
        assertEquals(h.versions(t), h2.versions(t))
        assertEquals(listOf(5L), h2.versions(Track("T\nx\\y", "A\tB"))[0].recent)
    }
    @Test fun metaFallbackShazam() {
        assertEquals(ScrobbleMeta("A", "T", null, MetaSource.SHAZAM), scrobbleMeta(Track("T", "A"), null))
    }
}
