package dev.deref.roomtone

import org.junit.Assert.assertEquals
import org.junit.Test

class LastFmSignTest {
    private val p = mapOf("api_key" to "a", "method" to "auth.getSession", "token" to "t")

    @Test fun firma() = assertEquals("2c828937331b73351d6500714bce502f", sign(p, "s"))
    @Test fun formatEscluso() {
        assertEquals(sign(p, "s"), sign(p + ("format" to "json") + ("callback" to "x"), "s"))
    }
}
