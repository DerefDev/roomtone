package dev.deref.roomtone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PickTest {
    private fun v(album: String, plays: Int, vararg recent: Long) = Version("Artist A", "Song A", album, plays, recent.toList())

    @Test fun vuotoSenzaScelta() = assertNull(pick(emptyList(), null))
    @Test fun unaVersione() {
        val a = v("Original", 3, 5, 4, 3)
        assertEquals(Pick.Auto(a), pick(listOf(a), null))
    }
    @Test fun sceltaSalvata() {
        val a = v("Original", 3, 9); val b = v("Compilation", 1, 1)
        assertEquals(Pick.Auto(b), pick(listOf(a, b), b.key))
        val gone = VersionKey("Artist A", "Song A", "Gone")
        assertEquals(Pick.Auto(Version("Artist A", "Song A", "Gone", 0, emptyList())), pick(listOf(a, b), gone))
    }
    @Test fun ultimi5Uguali() {
        val a = v("Original", 9, 50, 40, 30, 20, 10); val b = v("Compilation", 1, 5)
        assertEquals(Pick.Auto(a), pick(listOf(a, b), null))
    }
    @Test fun soloQuattroRecenti() {
        val a = v("Original", 9, 50, 40, 30, 20); val b = v("Compilation", 1, 5)
        val p = pick(listOf(a, b), null) as Pick.Ask
        assertEquals(a, p.options[0])
    }
    @Test fun ultimi5Misti() {
        val a = v("Original", 9, 50, 30, 20, 10); val b = v("Compilation", 1, 40)
        assertTrue(pick(listOf(a, b), null) is Pick.Ask)
    }
}
