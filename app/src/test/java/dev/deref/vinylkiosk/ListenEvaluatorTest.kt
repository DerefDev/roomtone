package dev.deref.vinylkiosk

import org.junit.Assert.assertEquals
import org.junit.Test

class ListenEvaluatorTest {
    // playedMs = last - first + 12_000
    private fun listen(played: Long) = Listen(Track("T", "A"), 100_000, 100_000 + played - 12_000, 5)

    @Test fun treRipetizioni() {
        val e = evaluate(listen(283_843), 94_000)
        assertEquals(Status.VALID, e.status)
        assertEquals(3, e.plays)
        assertEquals(listOf(90_000L, 184_000L, 278_000L), e.timestamps)
    }
    @Test fun menoDiMeta() = assertEquals(Status.DISCARDED, evaluate(listen(60_000), 240_000).status)
    @Test fun unaVolta() {
        val e = evaluate(listen(130_000), 240_000)
        assertEquals(Status.VALID, e.status); assertEquals(1, e.plays)
    }
    @Test fun quattroMinuti() {
        val e = evaluate(listen(600_000), 1_200_000)
        assertEquals(Status.VALID, e.status); assertEquals(1, e.plays)
    }
    @Test fun ambigue() {
        val e = evaluate(listen(235_000), 94_000)
        assertEquals(Status.DRAFT, e.status); assertEquals("ripetizioni ambigue", e.reason)
    }
    @Test fun durataNull() = assertEquals(Status.DRAFT, evaluate(listen(130_000), null).status)
}
