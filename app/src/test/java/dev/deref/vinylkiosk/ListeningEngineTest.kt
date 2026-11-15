package dev.deref.vinylkiosk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningEngineTest {

    private fun run(): List<Listen> {
        val out = mutableListOf<Listen>()
        val e = ListeningEngine { out += it }
        val lines = javaClass.getResourceAsStream("/shazam_session_01.tsv")!!.bufferedReader().readLines()
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
}
