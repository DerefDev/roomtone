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

    @Test fun titoloPerRicerca() {
        assertEquals("Song", searchTitle("Song (2025 remaster)"))
        assertEquals("Song", searchTitle("Song - 2025 Remaster"))
        assertEquals("Song", searchTitle("Song (feat. Guest One)"))
        assertEquals("Song (Live)", searchTitle("Song (Live)"))
        assertEquals("Song", coreTitle("Song - Acoustic Version"))
        assertEquals("Song", coreTitle("Song (con Guest One & Guest Two)"))
        assertEquals("Song", coreTitle("Song ft. Guest One"))
        assertEquals("Song", coreTitle("Song (feat. Guest One) - Ghost track"))
    }

    @Test fun artistiASchermo() {
        val t = Track("Track Title (feat. Guest One)", "Artist One & Artist Two")
        assertEquals("Track Title", stripFeat(t.title))
        assertEquals("Artist One, Artist Two feat. Guest One", displayArtist(t))
        assertEquals("Solo Artist feat. Guest Two, Guest Three", displayArtist(Track("Song 2024 (feat. Guest Two & Guest Three)", "Solo Artist")))
        assertEquals("DJ Alpha, Beta, Gamma, Delta", displayArtist(Track("Song", "DJ Alpha, Beta, Gamma & Delta")))
        assertEquals("North & South", displayArtist(Track("Song", "North & South")) { it == "North & South" })
        assertEquals("North, South", displayArtist(Track("Song", "North & South")))
        assertEquals("Lead Singer, the Band", displayArtist(Track("Song", "Lead Singer & the Band")))
        assertEquals("Lead Singer & the Band", displayArtist(Track("Song", "Lead Singer & the Band")) { true })
        assertEquals("X feat. North & South, A, B", displayArtist(Track("S (feat. North & South, A & B)", "X")) { it == "North & South" })
        assertEquals(listOf("Artist One & Artist Two", "Guest Two & Guest Three"), ampParts(Track("S (feat. Guest Two & Guest Three)", "Artist One & Artist Two")))
        assertEquals("Dance Track [New Edit]", stripFeat("Dance Track (feat. Guest Four & Guest Five) [New Edit]"))
    }

    @Test fun sigle() = assertEquals(1, bestTitle("Abc", listOf("Other", "A.B.C.")))

    @Test fun numeriDiversiNonCoincidono() {
        assertEquals(0.0, titleSim("Long Song Title Pt. 1", "Long Song Title Pt. 2"), 0.0)
        assertEquals(null, bestTitle("Long Song Title Pt. 1", listOf("Long Song Title Pt. 2")))
    }

    @Test fun artistiAParoleIntere() {
        assert(artistMatch("Artist One", "artist one"))
        assert(artistMatch("Artist One (2)", "Artist One"))
        assert(artistMatch("Artist One & Artist Two", "Artist Two"))
        assert(!artistMatch("Mace", "Macedonia"))
        assert(!artistMatch("Artist One", "Artist Two"))
        assertEquals(listOf("Artist One", "Artist Two", "Guest One", "Guest Two"),
            allArtists(Track("Song (feat. Guest One & Guest Two)", "Artist One & Artist Two")))
    }
}
