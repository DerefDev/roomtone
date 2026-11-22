package dev.deref.roomtone

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test fun patch() = assertTrue(Updates.newer("0.8.2", "0.8.1"))
    @Test fun equal() = assertFalse(Updates.newer("0.8.1", "0.8.1"))
    @Test fun numeric() = assertTrue(Updates.newer("0.10", "0.9"))
    @Test fun stableBeatsBeta() = assertTrue(Updates.newer("0.9", "0.9-beta1"))
    @Test fun betaVsBeta() = assertFalse(Updates.newer("0.9-beta2", "0.9-beta1"))
    @Test fun vPrefix() { assertTrue(Updates.newer("v0.9", "0.8.1")); assertFalse(Updates.newer("v0.8.1", "0.8.1")) }
    @Test fun major() = assertTrue(Updates.newer("1.0", "0.99"))
    @Test fun older() { assertFalse(Updates.newer("0.8.0", "0.8.1")); assertFalse(Updates.newer("0.9-beta1", "0.9")) }
    @Test fun missingZero() = assertFalse(Updates.newer("1.0", "1.0.0"))
}
