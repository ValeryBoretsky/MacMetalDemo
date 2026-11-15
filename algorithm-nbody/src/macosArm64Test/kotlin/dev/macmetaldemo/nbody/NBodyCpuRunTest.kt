package dev.macmetaldemo.nbody

import dev.macmetaldemo.application.*
import dev.macmetaldemo.cpu.DefaultCpuRuntimeFactory
import dev.macmetaldemo.domain.*
import dev.macmetaldemo.nbody.domain.*
import kotlin.test.*

class NBodyCpuRunTest {
    private val gpu = GpuStatusSink { }
    private fun request() = RunRequest("nbody", ExecutionMode.CPU_SINGLE, ParameterValues(mapOf("N" to ParameterValue.Integer(2), "S" to ParameterValue.Integer(2))))
    @Test fun progressWarmupAndRepeatRuns() {
        val algorithm = NBodyAlgorithm(DefaultCpuRuntimeFactory(), dev.macmetaldemo.metal.DefaultMetalRuntimeFactory())
        fun run(warm: Boolean): FloatArray {
            val run = algorithm.prepare(request(), gpu)
            assertFails { run.finalStateCopy() }
            if (warm) run.warmUp()
            val progress = mutableListOf<RunProgress>()
            run.execute(ProgressSink { progress.add(it) }, gpu)
            assertEquals(listOf(2L, 4L), progress.map { it.completedWork })
            assertFails { run.finalStateCopy() }
            run.checkAndSummarize()
            val result = run.finalStateCopy()
            result.positionMass[0] = 99f
            val copy = run.finalStateCopy().positionMass
            run.close()
            run.close()
            assertFails { run.finalStateCopy() }
            return copy
        }
        assertContentEquals(run(false), run(true))
    }
    @Test fun warmupUsesOneSeparateRange() {
        val ranges = mutableListOf<Int>()
        val runtime = object : CpuRuntime {
            override val workerCount = 1
            override fun forEachRange(elementCount: Int, action: (Int, Int) -> Unit) {
                ranges.add(elementCount)
                action(0, elementCount)
            }
            override fun close() {}
        }
        val run = dev.macmetaldemo.nbody.cpu.NBodyCpuRun(NBodyConfig(2, 1), runtime)
        run.warmUp()
        run.execute(ProgressSink { }, gpu)
        assertEquals(listOf(128, 2), ranges)
        run.close()
    }
    @Test fun runtimeFailureReleasesResources() {
        var closed = false
        val runtime = object : CpuRuntime {
            override val workerCount = 1
            override fun forEachRange(elementCount: Int, action: (Int, Int) -> Unit) { error("Test failure") }
            override fun close() { closed = true }
        }
        val run = dev.macmetaldemo.nbody.cpu.NBodyCpuRun(NBodyConfig(2, 1), runtime)
        assertFails { run.execute(ProgressSink { }, gpu) }
        assertTrue(closed)
        assertFails { run.finalStateCopy() }
    }
    @Test fun constructionFailureClosesRuntimeAndPreservesError() {
        val primary = IllegalStateException("worker count failure")
        val cleanup = IllegalStateException("cleanup failure")
        var closed = false
        val runtime = object : CpuRuntime {
            override val workerCount: Int get() = throw primary
            override fun forEachRange(elementCount: Int, action: (Int, Int) -> Unit) {}
            override fun close() { closed = true; throw cleanup }
        }
        val error = assertFailsWith<IllegalStateException> {
            dev.macmetaldemo.nbody.cpu.NBodyCpuRun(NBodyConfig(2, 1), runtime)
        }
        assertSame(primary, error)
        assertTrue(closed)
        assertEquals(listOf(cleanup), error.suppressedExceptions)
    }
    @Test fun copyFailureClosesRuntimeAndPreservesError() {
        val primary = IllegalStateException("copy failure")
        val cleanup = IllegalStateException("cleanup failure")
        var closed = false
        val runtime = object : CpuRuntime {
            override val workerCount = 1
            override fun forEachRange(elementCount: Int, action: (Int, Int) -> Unit) = action(0, elementCount)
            override fun close() { closed = true; throw cleanup }
        }
        val run = dev.macmetaldemo.nbody.cpu.NBodyCpuRun(NBodyConfig(2, 1), runtime) { throw primary }
        run.execute(ProgressSink { }, gpu)
        run.checkAndSummarize()
        val error = assertFailsWith<IllegalStateException> { run.finalStateCopy() }
        assertSame(primary, error)
        assertTrue(closed)
        assertEquals(listOf(cleanup), error.suppressedExceptions)
        assertFails { run.finalStateCopy() }
    }
    @Test fun validationAndFiniteSummary() {
        val algorithm = NBodyAlgorithm(DefaultCpuRuntimeFactory(), dev.macmetaldemo.metal.DefaultMetalRuntimeFactory())
        assertTrue(algorithm.validate(request().parameters) is ParameterValidation.Valid)
        val definitions = algorithm.parameters
        assertEquals(listOf("N", "S", "seed"), definitions.map { it.id })
        assertEquals(listOf(16384L, 1000L, 42L), definitions.map { it.defaultValue })
        val n = ParameterValue.Integer(2)
        val step = ParameterValue.Integer(1)
        assertTrue(algorithm.validate(ParameterValues(mapOf("N" to n))) is ParameterValidation.Invalid)
        assertTrue(algorithm.validate(ParameterValues(mapOf("N" to n, "S" to step, "X" to step))) is ParameterValidation.Invalid)
        assertTrue(algorithm.validate(ParameterValues(mapOf("N" to ParameterValue.Decimal(2.0), "S" to step))) is ParameterValidation.Invalid)
        val state = ParticleState(
            floatArrayOf(1f, 2f, 3f, 0.5f, 0.0000001f, 0.0000002f, 0.0000003f, 0.5f),
            floatArrayOf(4f, 5f, 6f, 0f, 0.0000004f, 0.0000005f, 0.0000006f, 0f),
        )
        val expected = listOf(
            1.0 + 0.0000001f.toDouble(), 2.0 + 0.0000002f.toDouble(),
            3.0 + 0.0000003f.toDouble(), 4.0 + 0.0000004f.toDouble(),
            5.0 + 0.0000005f.toDouble(), 6.0 + 0.0000006f.toDouble(),
        )
        assertEquals(expected, summarize(state).fields.map { it.value.toDouble() })
        state.velocity[0] = Float.NaN
        assertFails { summarize(state) }
        state.velocity[0] = 0f
        state.positionMass[0] = Float.POSITIVE_INFINITY
        assertFails { summarize(state) }
    }
}
