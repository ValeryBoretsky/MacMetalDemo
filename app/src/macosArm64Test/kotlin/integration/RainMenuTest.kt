package integration

import dev.macmetaldemo.app.di.createApplication
import dev.macmetaldemo.terminal.TerminalIo
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RainMenuTest {
    @Test fun thirdChoiceRunsRainAndRemembersItsOwnParameters() {
        val answers = listOf("3", "2", "31", "32", "24", "16385", "2048", "2", "3", "7",
            "3", "3", "", "", "", "", "", "", "0").iterator()
        val transcript = StringBuilder()
        val io = object : TerminalIo {
            override fun readLine() = if (answers.hasNext()) answers.next() else null
            override fun write(text: String) { transcript.append(text) }
        }
        assertEquals(0, createApplication(io).run())
        val output = transcript.toString()
        assertContains(output, "3  Rain Water - ")
        assertContains(output, "32 to 2048")
        assertContains(output, "1 to 16384")
        assertContains(output, "Parameters: width=32,height=24,dropsPerTick=2048,rainMass=2,ticks=3,seed=7")
        assertContains(output, "Field width [32]: Field height [24]: Drops per tick [2048]: Mass per drop [2]: Tick count [3]: Seed [7]:")
        val hashes = output.lines().filter { it.startsWith("Field hash: ") }
        assertEquals(2, hashes.size)
        assertEquals(hashes.first(), hashes.last())
        assertEquals(1, output.lines().count { it.startsWith("CPU (all cores):") && it.contains("GPU (Metal):") })
        assertContains(output, "Speed increase:")
        assertFalse(output.contains("Select a valid algorithm."))
        assertFalse(output.contains("Show visualization?"))
    }
}
