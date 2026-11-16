package dev.deref.roomtone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NormalizeTest {
    private fun k(t: String, a: String = "A") = normKey(Track(t, a))

    @Test fun uguali() {
        assertEquals(k("Song"), k("Song - Remastered 2011"))
        assertEquals(k("Song"), k("Song (feat. X)"))
        assertEquals(k("Song"), k("Song (2009 Remaster)"))
        assertEquals(k("Song"), k("Song - 2025 Remastered"))
        assertEquals(k("Song", "A"), k("Song", "A feat. B"))
        assertEquals(k("Città"), k("Citta"))
    }
    @Test fun diversi() {
        assertNotEquals(k("Song"), k("Song (Rmx)"))
        assertNotEquals(k("Song"), k("Song - Live"))
    }

    @Test fun primoArtistaEFeat() {
        assertEquals("artist one", firstArtist("Artist One & Artist Two"))
        assertEquals("artist one", firstArtist("Artist One feat. X"))
        assertEquals("Artist One", firstArtistRaw("Artist One, Artist Three"))
        assertEquals("Track Title", stripFeat("Track Title (feat. Guest One)"))
        assertEquals("Song", stripFeat("Song"))
    }
}
