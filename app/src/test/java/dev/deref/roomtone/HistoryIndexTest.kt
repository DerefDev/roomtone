package dev.deref.roomtone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryIndexTest {
    private fun HistoryIndex.add(title: String, album: String?, n: Int, uts: Long = 1) =
        repeat(n) { add(HistoryScrobble("Artist A", title, album, uts)) }

    private fun sample() = HistoryIndex().apply {
        add("Song - 2025 Remastered", "Anthology", 4)
        add("Song - 2025 Remastered", "Anthology (Deluxe)", 1)
        add("Song", null, 2)
    }

    @Test fun variantePiuFrequente() {
        val m = sample().lookup(Track("Song", "Artist A"))!!
        assertEquals(HistoryMatch("Artist A", "Song - 2025 Remastered", "Anthology", 5), m)
    }
    @Test fun assente() = assertNull(sample().lookup(Track("Other", "Artist A")))
    @Test fun parita_vinceLastUtsRecente() {
        val h = HistoryIndex().apply { add("Song", "Old", 2, 100); add("Song - Remastered", "New", 2, 200) }
        assertEquals("Song - Remastered", h.lookup(Track("Song", "Artist A"))!!.title)
    }
    @Test fun tsvRoundTrip() {
        val h = sample().apply { add(HistoryScrobble("A\tB", "T\nx\\y", "Al", 5)) }
        val h2 = HistoryIndex().apply { loadTsv(h.toTsv()) }
        assertEquals(h.size, h2.size)
        assertEquals(h.lookup(Track("Song", "Artist A")), h2.lookup(Track("Song", "Artist A")))
        assertEquals("T\nx\\y", h2.lookup(Track("T\nx\\y", "A\tB"))!!.title)
    }
    @Test fun metaFallbackShazam() {
        val m = scrobbleMeta(Track("T", "A"), null)
        assertEquals(ScrobbleMeta("A", "T", null, MetaSource.SHAZAM), m)
    }
}
