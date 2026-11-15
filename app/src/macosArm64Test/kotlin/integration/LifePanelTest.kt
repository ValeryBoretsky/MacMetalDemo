package integration

import dev.macmetaldemo.application.*
import dev.macmetaldemo.cpu.DefaultCpuRuntimeFactory
import dev.macmetaldemo.domain.*
import dev.macmetaldemo.life.GameOfLifeAlgorithm
import dev.macmetaldemo.metal.DefaultMetalRuntimeFactory
import dev.macmetaldemo.terminal.*
import kotlin.test.*

class LifePanelTest {
    @Test fun metalBoardFitsBelowMeasurementsAt80By60() = checkMetalPanel(80, 60, sideBySide = false)

    @Test fun metalBoardFitsBesideMeasurementsAt120By32() = checkMetalPanel(120, 32, sideBySide = true)

    private fun checkMetalPanel(columns: Int, rows: Int, sideBySide: Boolean) {
        val algorithm = GameOfLifeAlgorithm(DefaultCpuRuntimeFactory(), DefaultMetalRuntimeFactory())
        val parameters = lifeValues(512, 384, 1000, 1000)
        val request = RunRequest(algorithm.info.id, ExecutionMode.METAL, parameters, true)
        val descriptor = algorithm.describe(parameters)
        val snapshot = RunSnapshot(RunPhase.RUNNING, descriptor, BackendInfo(null, "Apple M1 Pro", 7u),
            RunProgress(10, 1000, 1_966_080), 0, 750_000_000L, null, GpuCommandStatus(GpuCommandState.SUBMITTED, null))
        val frame = VisualizationFrame(10, BooleanArray(80 * 60) { it == 0 })
        var finished = false
        val handle = object : RunHandle {
            override fun latest() = snapshot
            override fun latestVisualization() = frame
            override fun outcome() = if (finished) RunOutcome.Failure("Done.", false) else null
            override fun awaitCompletion() = checkNotNull(outcome())
        }
        val writes = mutableListOf<String>()
        val io = object : TerminalIo {
            override fun readLine(): String? = null
            override fun write(text: String) { writes.add(text) }
        }
        val screen = object : TerminalScreen {
            override val active = true
            override fun clear() = Unit
            override fun close() = Unit
        }
        val resources = ResourceSnapshot(750_000_000L, (0 until 10).map { CoreLoad(it, 30.0) }, 40.0, null,
            listOf(GpuLoad(7u, 16, 75.0)))
        LiveRunDriver(RunWorker { handle }, MonotonicClock { 750_000_000L }, ResourceSampler { resources },
            WaitStrategy { finished = true }, io, { TerminalCapabilities(true, "xterm", columns, rows, false) },
            screen, { algorithm.info.name }).run(request, algorithm.visualizationTitle(parameters))

        val picture = assertNotNull(writes.firstOrNull { "Full board | Detail" in it }, "The terminal must show the board.")
        val lines = picture.removePrefix("\u001b[H").removeSuffix("\u001b[J").split("\u001b[K\r\n").dropLast(1)
        val label = "Full board | Detail - 10/1000"
        if (sideBySide) assertContains(lines.first(), label) else assertTrue(lines.any { it.trim() == label })
        assertTrue(lines.size < rows)
        assertTrue(lines.all { it.length < columns })
        assertContains(picture, "*")
        for (field in descriptor.key.canonicalParameters.split(',') + descriptor.key.numericalSettings.split(',')) {
            assertContains(picture, field)
        }
        for (required in listOf("Parameters:", "Settings:", "196608000 cell updates", "Apple M1 Pro",
            "Progress: 1%", "million cell updates/s", "System GPU  75%", "GPU history:", "GPU command duration:", "App CPU")) {
            assertContains(picture, required)
        }
        for (core in 0 until 10) assertContains(picture, "System CPU $core ")
    }
}
