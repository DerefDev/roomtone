package dev.deref.roomtone

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrobbleEditsTest {
    private val all = EditOptions(true, true, true, true, true, true, true, true)

    @Test fun remaster() {
        val o = EditOptions()
        assertEquals("Song A", cleanTitle("Song A - 2011 Remaster", o))
        assertEquals("Song A", cleanTitle("Song A - Remastered 2025", o))
        assertEquals("Song A", cleanTitle("Song A (Remastered)", o))
        assertEquals("Song A", cleanTitle("Song A [2009 Remaster]", o))
        assertEquals("Song A - 2011 Remaster", cleanTitle("Song A - 2011 Remaster", EditOptions(remaster = false)))
    }

    @Test fun versionsLiveExplicit() {
        assertEquals("Song A", cleanTitle("Song A - Radio Edit", EditOptions(versions = true)))
        assertEquals("Song A", cleanTitle("Song A (Mono)", EditOptions(versions = true)))
        assertEquals("Song A (Radio Edit)", cleanTitle("Song A (Radio Edit)", EditOptions()))
        assertEquals("Song A", cleanTitle("Song A - Live", EditOptions(live = true)))
        assertEquals("Song A", cleanTitle("Song A (Live at Place B)", EditOptions(live = true)))
        assertEquals("Song A", cleanTitle("Song A (Explicit)", EditOptions()))
        assertEquals("Song A", cleanTitle("Song A [Clean]", EditOptions()))
    }

    @Test fun introOutro() {
        val o = EditOptions(introOutro = true)
        assertEquals("Song A", cleanTitle("Song A - Intro", o))
        assertEquals("Song A", cleanTitle("Song A (Outro)", o))
        assertEquals("Intro", cleanTitle("Intro", o))
        assertEquals("Song A - Intro", cleanTitle("Song A - Intro", EditOptions()))
    }

    @Test fun feat() {
        val o = EditOptions(featToArtist = true)
        assertEquals("Song A", cleanTitle("Song A (feat. Artist B)", o))
        assertEquals("Artist A feat. Artist B", cleanArtist("Artist A", o, "Song A [ft. Artist B]"))
        assertEquals("Song A (feat. Artist B)", cleanTitle("Song A (feat. Artist B)", EditOptions()))
        assertEquals("Artist A", cleanArtist("Artist A", EditOptions(), "Song A (feat. Artist B)"))
    }

    @Test fun album() {
        val o = EditOptions()
        assertEquals("Album A", cleanAlbum("Album A (Deluxe Edition)", o))
        assertEquals("Album A", cleanAlbum("Album A (Expanded Edition)", o))
        assertEquals("Album A", cleanAlbum("Album A (25th Anniversary Edition)", o))
        assertEquals("Album A", cleanAlbum("Album A [Bonus Track Version]", o))
        assertEquals("Album A", cleanAlbum("Album A (Special Edition)", o))
        assertEquals("Album A (Deluxe)", cleanAlbum("Album A (Deluxe)", EditOptions(albumEditions = false)))
    }

    @Test fun artist() {
        assertEquals("Artist A", cleanArtist("Artist A & Artist B", EditOptions(mainArtistOnly = true)))
        assertEquals("Artist A & Artist B", cleanArtist("Artist A & Artist B", EditOptions()))
    }
}
