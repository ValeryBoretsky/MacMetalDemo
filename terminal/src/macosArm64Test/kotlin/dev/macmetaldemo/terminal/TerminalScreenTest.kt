package dev.macmetaldemo.terminal

import kotlin.test.*

class TerminalScreenTest {
    private class Output : TerminalIo {
        var text = ""
        override fun readLine(): String? = null
        override fun write(text: String) { this.text += text }
    }

    @Test fun unsuitableTerminalsRemainPlain() {
        for (caps in listOf(
            TerminalCapabilities(false, "xterm", 80, 24, false),
            TerminalCapabilities(true, null, 80, 24, false),
            TerminalCapabilities(true, "dumb", 80, 24, false),
            TerminalCapabilities(true, "unknown-terminal", 80, 24, false),
            TerminalCapabilities(true, "xterm", 0, 24, false)
        )) {
            var opened = false
            val screen = openTerminalScreen(Output(), caps, { opened = true; true }, {})
            assertFalse(opened)
            assertFalse(screen.active)
        }
    }

    @Test fun failedEntryRemainsPlain() {
        var closes = 0
        val output = Output()
        val screen = openTerminalScreen(output, TerminalCapabilities(true, "xterm", 80, 24, false), { false }, { closes++ })
        screen.clear()
        screen.close()
        assertFalse(screen.active)
        assertEquals("", output.text)
        assertEquals(0, closes)
    }

    @Test fun successfulEntryClearsAndClosesOnce() {
        var opens = 0
        var closes = 0
        val output = Output()
        val screen = openTerminalScreen(output, TerminalCapabilities(true, "xterm-256color", 80, 24, false),
            { opens++; true }, { closes++ })
        assertTrue(screen.active)
        screen.clear()
        assertEquals("\u001b[2J\u001b[H", output.text)
        screen.close()
        screen.close()
        screen.clear()
        assertFalse(screen.active)
        assertEquals(1, opens)
        assertEquals(1, closes)
        assertEquals("\u001b[2J\u001b[H", output.text)
    }
}
