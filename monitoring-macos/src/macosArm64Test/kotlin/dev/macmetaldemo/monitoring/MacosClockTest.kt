package dev.macmetaldemo.monitoring

import kotlin.test.*

class MacosClockTest {
    @Test fun clockDoesNotMoveBackwards() {
        val clock = MacosClock()
        val first = clock.nowNs()
        assertTrue(clock.nowNs() >= first)
    }
}
