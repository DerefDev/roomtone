package dev.deref.roomtone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumsTest {
    private fun h(title: String, year: Int, vararg f: String) = DiscogsHit(1, title, year, f.toList(), null)

    @Test fun albumPiuVecchio() {
        val r = pickMaster(listOf(h("Best Of", 2020, "Album", "Compilation"), h("B", 2015, "Album"), h("A", 2010, "Album")), null)
        assertEquals("A", r?.title)
    }
    @Test fun ripiegoEpPoiSinglePoiCompilation() {
        assertEquals("EP", pickMaster(listOf(h("S", 2001, "Single"), h("EP", 2005, "EP")), null)?.title)
        assertEquals("S", pickMaster(listOf(h("C", 2001, "Compilation"), h("S", 2005, "Single")), null)?.title)
        assertEquals("C", pickMaster(listOf(h("C", 2001, "Album", "Compilation")), null)?.title)
    }
    @Test fun lastFmVince() {
        val l = listOf(h("Old", 2010, "Album"), h("New (Deluxe)", 2015, "Album"))
        assertEquals("New (Deluxe)", pickMaster(l, "New")?.title)
        assertEquals("Old", pickMaster(l, "Altro")?.title)
    }
    @Test fun vuota() = assertNull(pickMaster(emptyList(), "x"))
    @Test fun puliziaDiscogs() {
        assertEquals("Singer", cleanDiscogsArtist("Singer (2)*"))
        assertEquals("Album Title", discogsTitle("Artist One - Album Title"))
        assertEquals("A - B", discogsTitle("X - A - B"))
    }
}
