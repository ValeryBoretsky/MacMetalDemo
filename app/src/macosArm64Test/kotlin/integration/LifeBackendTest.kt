package integration

import dev.macmetaldemo.application.*
import dev.macmetaldemo.cpu.DefaultCpuRuntimeFactory
import dev.macmetaldemo.domain.*
import dev.macmetaldemo.life.GameOfLifeAlgorithm
import dev.macmetaldemo.metal.DefaultMetalRuntimeFactory
import dev.macmetaldemo.terminal.renderVisualization
import kotlin.test.*

internal fun lifeValues(width: Long, height: Long, population: Long, generations: Long, seed: Long = 42) =
    ParameterValues(mapOf("width" to width, "height" to height, "initialLiveCells" to population,
        "generations" to generations, "seed" to seed).mapValues { ParameterValue.Integer(it.value) })

private data class LifeResult(val board: FloatArray, val fields: List<ResultFieldValue>,
                              val frames: List<VisualizationFrame>, val progress: List<RunProgress>)

class LifeBackendTest {
    private val algorithm = GameOfLifeAlgorithm(DefaultCpuRuntimeFactory(), DefaultMetalRuntimeFactory())

    private fun run(mode: ExecutionMode, values: ParameterValues, capture: Boolean = false): LifeResult {
        val frames = mutableListOf<VisualizationFrame>()
        val updates = mutableListOf<RunProgress>()
        val gpu = GpuStatusSink { }
        val prepared = algorithm.prepare(RunRequest("game-of-life", mode, values, capture), gpu)
        try {
            if (capture) prepared.attachVisualization(object : VisualizationPort {
                override fun captureRequested() = true
                override fun publish(frame: VisualizationFrame) { frames.add(frame) }
                override fun disable() = fail("Cannot capture the board.")
            })
            prepared.warmUp()
            prepared.execute(ProgressSink { updates.add(it) }, gpu)
            val summary = prepared.checkAndSummarize()
            return LifeResult(prepared.finalBoardCopy(), summary.fields, frames, updates)
        } finally {
            prepared.close()
        }
    }

    @Test fun allModesReproduceTheWorkedExampleAfterWarmUp() {
        val values = lifeValues(5, 4, 7, 2)
        val cells = "00100110010010011001".map { if (it == '1') 1f else 0f }.toFloatArray()
        for (mode in ExecutionMode.entries) {
            val result = run(mode, values)
            assertContentEquals(cells, result.board)
            assertEquals(listOf(ResultFieldValue("liveCells", "8"), ResultFieldValue("boardHash", "bd6d954ad643a01d")), result.fields)
            assertEquals(listOf(RunProgress(1, 2, 20), RunProgress(2, 2, 40)), result.progress)
        }
    }

    @Test fun unevenRangesAndOddAndEvenBufferExchangesPreserveEveryCell() {
        for (generations in listOf(3L, 4L)) {
            val values = lifeValues(17, 19, 87, generations)
            val reference = run(ExecutionMode.CPU_SINGLE, values, true)
            for (mode in ExecutionMode.entries) {
                val observed = run(mode, values, true)
                assertContentEquals(reference.board, observed.board)
                assertContentEquals(reference.board, run(mode, values).board)
                assertEquals(reference.progress, observed.progress)
                assertEquals(generations.toInt(), observed.frames.size)
                for ((index, frame) in observed.frames.withIndex()) {
                    assertEquals(index + 1L, frame.completedUnits)
                    assertEquals(renderVisualization(reference.frames[index], false), renderVisualization(frame, false))
                }
            }
        }
    }

    @Test fun everyModeDrawsTheFiveGliderCellsAndLeavesAllPaddingBlank() {
        val expected = listOf("00000 00000 10100 01100 01000", "00000 00000 00100 10100 01100",
            "00000 00000 01000 00110 01100", "00000 00000 00100 00010 01110")
        for (mode in ExecutionMode.entries) {
            val frames = run(mode, lifeValues(5, 5, 5, 4, 120509), true).frames
            assertEquals(4, frames.size)
            for ((index, frame) in frames.withIndex()) {
                val rows = expected[index].split(' ')
                assertEquals(index + 1L, frame.completedUnits)
                for (ascii in listOf(false, true)) {
                    val picture = List(15) { y ->
                        if (y !in 5..9) " ".repeat(40) else {
                            val occupied = if (ascii) "* " else "⣿ "
                            " ".repeat(15) + rows[y - 5].map { if (it == '1') occupied else "  " }.joinToString("") + " ".repeat(15)
                        }
                    }
                    assertEquals(picture, renderVisualization(frame, ascii))
                }
            }
        }
    }

    @Test fun defaultBoardHasTheSameGliderAndPopulationsInEveryMode() {
        val populations = listOf(1066, 1132, 1264, 1198)
        val reference = run(ExecutionMode.CPU_SINGLE, lifeValues(512, 384, 1000, 4), true)
        for (mode in ExecutionMode.entries) {
            for ((index, population) in populations.withIndex()) {
                val result = run(mode, lifeValues(512, 384, 1000, index + 1L), true)
                assertEquals(population, result.board.count { it == 1f })
                assertEquals(index + 1L, result.frames.last().completedUnits)
                assertEquals(renderVisualization(reference.frames[index], false), renderVisualization(result.frames.last(), false))
                if (index == 3) assertContentEquals(reference.board, result.board)
            }
        }
    }
}
