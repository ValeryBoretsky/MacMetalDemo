package integration

import dev.macmetaldemo.app.di.createApplication
import dev.macmetaldemo.application.*
import dev.macmetaldemo.cpu.DefaultCpuRuntimeFactory
import dev.macmetaldemo.domain.*
import dev.macmetaldemo.life.GameOfLifeAlgorithm
import dev.macmetaldemo.metal.DefaultMetalRuntimeFactory
import dev.macmetaldemo.terminal.TerminalIo
import kotlin.test.*

class LifeInputsTest {
    private val algorithm = GameOfLifeAlgorithm(DefaultCpuRuntimeFactory(), DefaultMetalRuntimeFactory())

    @Test fun invalidDirectRequestsCannotPrepareRuns() {
        val valid = lifeValues(20, 15, 300, 1).asMap()
        val invalid = listOf(valid - "seed", valid + ("extra" to ParameterValue.Integer(0)),
            valid + ("width" to ParameterValue.Integer(Long.MAX_VALUE)),
            valid + ("height" to ParameterValue.Integer(2)),
            valid + ("initialLiveCells" to ParameterValue.Integer(301)),
            valid + ("generations" to ParameterValue.Decimal(1.5)),
            valid + ("generations" to ParameterValue.Integer(1_000_001)),
            valid + ("seed" to ParameterValue.Integer(-1)), valid + ("seed" to ParameterValue.Integer(4294967296)))
        for (fields in invalid) {
            val values = ParameterValues(fields)
            assertIs<ParameterValidation.Invalid>(algorithm.validate(values))
            assertFailsWith<IllegalArgumentException> {
                algorithm.prepare(RunRequest("game-of-life", ExecutionMode.CPU_SINGLE, values), GpuStatusSink { })
            }
        }
    }

    @Test fun descriptorUsesAllFiveCanonicalParametersAndExactWorkAtBothLimits() {
        val low = algorithm.describe(lifeValues(3, 3, 0, 1, 0))
        assertEquals(9L, low.exactWorkCount)
        val high = algorithm.describe(lifeValues(4096, 4096, 16777216, 1000000, 4294967295))
        assertEquals(16777216000000L, high.exactWorkCount)
        assertEquals(1000000L, high.progressTotal)
        assertEquals("generations", high.progressUnit)
        assertEquals(ComparisonKey("game-of-life", "1", "width=4096,height=4096,initialLiveCells=16777216,generations=1000000,seed=4294967295",
            "rule=B3/S23,boundary=torus,storage=binary-fp32,generator=patterns-lcg32-v1", "cell-updates-v1"), high.key)
        assertEquals(WorkDefinition("cell-updates-v1", "cell updates", 1000000.0, "million cell updates/s"), high.work)
    }

    private fun session(vararg answers: String): String {
        val inputs = answers.iterator()
        val output = StringBuilder()
        val io = object : TerminalIo {
            override fun readLine() = if (inputs.hasNext()) inputs.next() else null
            override fun write(text: String) { output.append(text) }
        }
        assertEquals(0, createApplication(io).run())
        return output.toString()
    }

    @Test fun countUsesBothDimensionsAndRemembersTheAcceptedCount() {
        val text = session("2","1","20","15","1000","45","1","", "2","1","8","8","","1","",
            "2","1","3","3","","1","", "0")
        assertContains(text, "Board width [512]: Board height [384]: Live cells at start [300]: " +
            "Enter a count from 0 to 300 for this 20 by 15 board.\nLive cells at start [300]: Generation count [1000]: Seed [42]: ")
        assertContains(text, "width=8,height=8,initialLiveCells=45,generations=1,seed=42")
        assertContains(text, "width=3,height=3,initialLiveCells=9,generations=1,seed=42")
    }

    @Test fun enterAcceptsTheFirstDefaultAndEofStopsAtThePendingPrompt() {
        assertContains(session("2","1","","","","1",""), "width=512,height=384,initialLiveCells=1000,generations=1,seed=42")
        assertContains(session("2","1","20","15","","1",""), "width=20,height=15,initialLiveCells=300,generations=1,seed=42")
        val unfinished = session("2","1","20","15")
        assertTrue(unfinished.endsWith("Live cells at start [300]: "))
        assertFalse(unfinished.contains("Calculation:"))
    }

    @Test fun sessionKeepsAlgorithmDefaultsAndBaselinesSeparate() {
        val text = session("1","1","3","1","7", "2","2","5","4","7","2","42", "1","2","","","",
            "2","1","","","","","", "2","2","","","","","", "0")
        assertContains(text, "1  N-body - particles under mutual gravity\n2  Game of Life - cells evolve from their eight neighbors")
        assertContains(text, "Particle count [3]: Step count [1]: Seed [7]:")
        assertFalse(text.contains("Show visualization?"))
        val speeds = text.lines().filter { it.startsWith("Speed increase:") }
        assertEquals(5, speeds.size)
        assertContains(speeds[1], "matching CPU baseline unavailable")
        assertTrue(speeds[2].substringAfter(": ").toDouble() > 0)
        assertTrue(speeds[4].substringAfter(": ").toDouble() > 0)
    }
}
