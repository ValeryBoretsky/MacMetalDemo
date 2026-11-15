package integration

import dev.macmetaldemo.application.*
import dev.macmetaldemo.cpu.DefaultCpuRuntimeFactory
import dev.macmetaldemo.domain.*
import dev.macmetaldemo.metal.DefaultMetalRuntimeFactory
import dev.macmetaldemo.nbody.NBodyAlgorithm
import dev.macmetaldemo.nbody.domain.ParticleState
import kotlin.test.*

class NBodyVisualizationTest {
    @Test fun capturePreservesFinalBitsForEveryBackend() {
        val algorithm = NBodyAlgorithm(DefaultCpuRuntimeFactory(), DefaultMetalRuntimeFactory())
        val values = ParameterValues(mapOf("N" to ParameterValue.Integer(16), "S" to ParameterValue.Integer(3), "seed" to ParameterValue.Integer(0)))
        for (mode in ExecutionMode.entries) {
            val frames = mutableListOf<VisualizationFrame>()
            fun run(capture: Boolean): ParticleState {
                val gpu = GpuStatusSink { }
                val run = algorithm.prepare(RunRequest("nbody", mode, values), gpu)
                try {
                    if (capture) run.attachVisualization(object : VisualizationPort {
                        override fun captureRequested() = true
                        override fun publish(frame: VisualizationFrame) { frames.add(frame) }
                        override fun disable() { fail("Capture failed") }
                    })
                    run.warmUp()
                    run.execute(ProgressSink { }, gpu)
                    run.checkAndSummarize()
                    return run.finalStateCopy()
                } finally { run.close() }
            }
            val without = run(false)
            val with = run(true)
            assertContentEquals(without.positionMass.map { it.toBits() }, with.positionMass.map { it.toBits() }, mode.toString())
            assertContentEquals(without.velocity.map { it.toBits() }, with.velocity.map { it.toBits() }, mode.toString())
            assertEquals(listOf(1L, 2L, 3L), frames.map { it.completedUnits })
            assertTrue(frames.any { frame -> (0 until 60).any { y -> (0 until 80).any { x -> frame.isSet(x, y) } } })
        }
    }
    @Test fun drawingFailureDisablesCaptureAndCalculationContinues() {
        val algorithm = NBodyAlgorithm(DefaultCpuRuntimeFactory(), DefaultMetalRuntimeFactory())
        val values = ParameterValues(mapOf("N" to ParameterValue.Integer(8), "S" to ParameterValue.Integer(3)))
        for (mode in ExecutionMode.entries) {
            val gpu = GpuStatusSink { }
            val run = algorithm.prepare(RunRequest("nbody", mode, values), gpu)
            var disabled = 0
            var requests = 0
            try {
                run.attachVisualization(object : VisualizationPort {
                    override fun captureRequested(): Boolean { requests++; return true }
                    override fun publish(frame: VisualizationFrame) { error("Drawing failed") }
                    override fun disable() { disabled++ }
                })
                val progress = mutableListOf<RunProgress>()
                run.execute(ProgressSink { progress.add(it) }, gpu)
                run.checkAndSummarize()
                assertEquals(3, progress.size)
                assertEquals(1, disabled)
                assertEquals(1, requests)
            } finally { run.close() }
        }
    }

}
