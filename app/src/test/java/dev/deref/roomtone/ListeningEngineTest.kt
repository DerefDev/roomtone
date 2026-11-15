package dev.deref.roomtone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningEngineTest {

    private fun run(res: String = "/shazam_session_01.tsv"): List<Listen> {
        val out = mutableListOf<Listen>()
        val e = ListeningEngine { out += it }
        val lines = javaClass.getResourceAsStream(res)!!.bufferedReader().readLines()
        for (l in lines) {
            val c = l.split('\t')
            if (c.size < 4) continue
            val t = c[0].toLong()
            if (c[1] == "match") e.onMatch(c[2], c[3], t) else if (c[1] == "beat") e.onBeat(c[3], t)
        }
        e.finish()
        return out
    }

    // Real session (anonymized): 11 distinct tracks, two 1-beat samples
    // (Track 05, Track 07), one track repeated 3x back to back (Track 04),
    // one track resumed after a ~2 min pause (Track 11).
    @Test
    fun sequenza() {
        val got = run()
        val exp = listOf(
            "Track 01" to 4, "Track 02" to 4, "Track 02" to 5, "Track 03" to 15,
            "Track 04" to 23, "Track 01" to 10, "Track 06" to 15, "Track 08" to 15,
            "Track 09" to 16, "Track 10" to 10, "Track 11" to 10, "Track 11" to 10,
        )
        assertEquals(exp, got.map { it.track.title to it.beats })
        // 3 riproduzioni consecutive di ~94 s → un solo tratto di ~283 s
        assertTrue(got.first { it.track.title == "Track 04" }.playedMs in 280_000..286_000)
        // sample di 1 battito scartati
        assertFalse(got.any { it.track.title == "Track 05" || it.track.title == "Track 07" })
    }

    // Real session (anonymized): a 2-beat sample (Track 09, < 30 s), quiet
    // passages producing scattered neutral beats inside Track 10 and Track 11.
    @Test
    fun interruzioniBrevi() {
        val got = run("/shazam_session_02.tsv")
        val exp = listOf(
            "Track 01" to 21, "Track 02" to 15, "Track 03" to 23, "Track 04" to 19,
            "Track 05" to 9, "Track 07" to 9, "Track 08" to 16, "Track 10" to 11,
            "Track 11" to 13, "Track 12" to 18, "Track 13" to 11,
        )
        assertEquals(exp, got.map { it.track.title to it.beats })
    }

    @Test
    fun schermoSubitoAlPrimoBattito() {
        val e = ListeningEngine { }
        e.onBeat("Track A di Artist A", 0)
        assertEquals(Track("Track A", "Artist A"), e.displayTrack) // mostrato subito
        assertEquals(null, e.currentTrack) // ma non ancora un ascolto
        e.onBeat("Track A di Artist A", 12_000)
        assertEquals(Track("Track A", "Artist A"), e.currentTrack)
        e.onBeat("Track B di Artist B", 24_000) // sample: schermo cambia, ascolto no
        assertEquals("Track B", e.displayTrack?.title)
        assertEquals("Track A", e.currentTrack?.title)
        e.onBeat("Track A di Artist A", 36_000)
        assertEquals("Track A", e.displayTrack?.title)
        repeat(3) { e.onBeat("5 canzoni trovate", 48_000L + it * 12_000) } // silenzio → chiuso
        assertEquals(null, e.displayTrack)
    }

    @Test
    fun separatoreInglese() {
        val out = mutableListOf<Listen>()
        val e = ListeningEngine(separator = " by ") { out += it }
        repeat(2) { e.onBeat("Track A by Artist A", it * 20_000L) } // ≥ 30 s di ascolto
        repeat(3) { e.onBeat("3 songs found", 32_000L + it * 12_000) }
        e.finish()
        assertEquals(listOf(Track("Track A", "Artist A")), out.map { it.track })
    }
}
