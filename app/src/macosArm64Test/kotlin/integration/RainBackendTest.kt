package integration

import dev.macmetaldemo.application.*
import dev.macmetaldemo.cpu.DefaultCpuRuntimeFactory
import dev.macmetaldemo.domain.*
import dev.macmetaldemo.metal.DefaultMetalRuntimeFactory
import dev.macmetaldemo.rain.*
import dev.macmetaldemo.terminal.renderVisualization
import kotlin.test.*

private fun rainValues(ticks: Long, seed: Long, rain: Long = 2L, drops: Long = 19) = ParameterValues(
    mapOf("width" to 33L, "height" to 25L, "dropsPerTick" to drops, "rainMass" to rain, "ticks" to ticks, "seed" to seed)
        .mapValues { ParameterValue.Integer(it.value) })

private class RainResult(val state: RainSnapshot, val fields: List<ResultFieldValue>, val frame: VisualizationFrame?)

class RainBackendTest {
    private val algorithm = RainWaterAlgorithm(DefaultCpuRuntimeFactory(), DefaultMetalRuntimeFactory())

    private fun run(mode: ExecutionMode, ticks: Int, seed: Long, draw: Boolean, drops: Long = 19): RainResult {
        val values = rainValues(ticks.toLong(), seed, drops = drops)
        var checkedTicks = 0
        var completedCommands = 0
        var lastFrame: VisualizationFrame? = null
        val gpu = GpuStatusSink { if (it.state == GpuCommandState.COMPLETED) completedCommands++ }
        val prepared = algorithm.prepare(RunRequest("rain-water", mode, values, draw), gpu)
        try {
            if (draw) prepared.attachVisualization(object : VisualizationPort {
                override fun captureRequested() = true
                override fun publish(frame: VisualizationFrame) { lastFrame = frame }
                override fun disable() = fail("Cannot draw the rain field.")
            })
            prepared.warmUp()
            prepared.execute(ProgressSink {
                checkedTicks++
                assertEquals(RunProgress(checkedTicks.toLong(), ticks.toLong(), checkedTicks * 825L), it)
            }, gpu)
            assertEquals(ticks, checkedTicks)
            assertEquals(if (mode == ExecutionMode.METAL) ticks else 0, completedCommands)
            if (draw) assertEquals(ticks.toLong(), lastFrame?.completedUnits)
            val summary = prepared.checkAndSummarize()
            return RainResult(prepared.finalStateCopy(), summary.fields, lastFrame)
        } finally { prepared.close() }
    }

    private fun sameState(expected: RainResult, actual: RainResult) {
        assertContentEquals(expected.state.mass, actual.state.mass)
        assertContentEquals(expected.state.admitted, actual.state.admitted)
        assertContentEquals(expected.state.drained, actual.state.drained)
        assertEquals(expected.fields, actual.fields)
    }

    @Test fun everyBackendMatchesEveryCellAndCounterOnOddFields() {
        for (seed in listOf(0L, 42L, 4294967295L)) {
            for (ticks in (1..26).toList() + 200) {
                val reference = run(ExecutionMode.CPU_SINGLE, ticks, seed, true)
                for (mode in listOf(ExecutionMode.CPU_PARALLEL, ExecutionMode.METAL)) {
                    val result = run(mode, ticks, seed, true)
                    sameState(reference, result)
                    for (ascii in listOf(false, true)) {
                        assertEquals(renderVisualization(reference.frame!!, ascii), renderVisualization(result.frame!!, ascii))
                    }
                }
                if (ticks == 200) {
                    assertTrue(reference.state.drained.sum() > 0)
                    assertTrue(reference.state.mass.sum() > 0f)
                }
            }
        }
    }

    @Test fun captureDoesNotChangeAnyResult() {
        for (mode in ExecutionMode.entries) sameState(run(mode, 61, 42, true), run(mode, 61, 42, false))
    }

    @Test fun thousandsOfDropsConserveMassAcrossAllModes() {
        val ticks = 37
        val reference = run(ExecutionMode.CPU_SINGLE, ticks, 77, false, 4096)
        for (mode in listOf(ExecutionMode.CPU_PARALLEL, ExecutionMode.METAL)) {
            sameState(reference, run(mode, ticks, 77, false, 4096))
        }
        val fields = reference.fields.associate { it.id to it.value }
        val admitted = fields.getValue("injectedMass").toLong()
        assertEquals(4096L * 2 * ticks, admitted + fields.getValue("rejectedMass").toLong())
        assertEquals(admitted, fields.getValue("drainedMass").toLong() + fields.getValue("storedMass").toLong())
    }

    @Test fun metalAddsOneUnitBeyondTheFloatPrecisionLimit() {
        val metal = DefaultMetalRuntimeFactory()
        val buffers = mutableListOf<MetalBuffer>()
        val factory = object : MetalRuntimeFactory by metal {
            override fun open(source: String, entryPoint: String): MetalProgram {
                val program = metal.open(source, entryPoint)
                return object : MetalProgram by program {
                    override fun createBuffer(byteCount: Long): MetalBuffer =
                        program.createBuffer(byteCount).also { buffers.add(it) }
                }
            }
        }
        val values = rainValues(1, 77, rain = 1, drops = 1)
        val request = RunRequest("rain-water", ExecutionMode.METAL, values)
        val gpu = GpuStatusSink {}
        val run = RainWaterAlgorithm(DefaultCpuRuntimeFactory(), factory).prepare(request, gpu)
        try {
            val width = 33
            val height = 25
            val initial = 1 shl 24
            val words = FloatArray(width) { Float.fromBits(initial) }
            buffers[3].write(words)
            buffers[4].write(words)
            val mass = FloatArray(width * height)
            mass[width * (height - 1) + 1] = 1f
            buffers[0].write(mass)
            run.execute(ProgressSink {}, gpu)
            val drops = FloatArray(width)
            buffers[5].readInto(drops)
            buffers[3].readInto(words)
            assertContentEquals(IntArray(width) { initial + drops[it].toInt() }, IntArray(width) { words[it].toRawBits() })
            buffers[4].readInto(words)
            assertContentEquals(IntArray(width) { if (it == 1) initial + 1 else initial }, IntArray(width) { words[it].toRawBits() })
        } finally { run.close() }
    }

    @Test fun descriptorAndValidationRejectDifferentWorkloads() {
        val values = rainValues(200, 42)
        val descriptor = algorithm.describe(values)
        assertEquals(165000L, descriptor.exactWorkCount)
        assertEquals("cell ticks", descriptor.work.unit)
        assertNotEquals(descriptor.key, algorithm.describe(rainValues(200, 42, 11)).key)
        assertNotEquals(descriptor.key, algorithm.describe(rainValues(200, 42, drops = 20)).key)
        val good = values.asMap()
        for (bad in listOf(good - "width", good + ("seed" to ParameterValue.Integer(4294967296L)),
            good + ("ticks" to ParameterValue.Integer(Long.MAX_VALUE)), good + ("unknown" to ParameterValue.Integer(4L)),
            good + ("rainMass" to ParameterValue.Decimal(1.5)))) {
            assertIs<ParameterValidation.Invalid>(algorithm.validate(ParameterValues(bad)))
        }
    }
}
