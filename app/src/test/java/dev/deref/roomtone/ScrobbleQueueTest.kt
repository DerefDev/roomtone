package dev.deref.roomtone

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrobbleQueueTest {
    @Test fun idsAndSeconds() {
        val l = Listen(Track("Song A", "Artist A"), 1_000_000, 1_500_000, 5)
        val e = Evaluation(Status.VALID, Reason.OK, 2, listOf(990_000, 1_230_000), 240_000)
        assertEquals(listOf("1000000-0" to 990L, "1000000-1" to 1230L), queueIds(l, e))
    }

    @Test fun notes() = assertEquals("timestamp too old", ignoredNote(3))
}
