package dev.macmetaldemo.nbody

import dev.macmetaldemo.application.*
import dev.macmetaldemo.cpu.DefaultCpuRuntimeFactory
import dev.macmetaldemo.domain.*
import dev.macmetaldemo.metal.DefaultMetalRuntimeFactory
import kotlin.test.*

class NBodySeedTest {
    private val algorithm = NBodyAlgorithm(DefaultCpuRuntimeFactory(), DefaultMetalRuntimeFactory())
    private fun values(seed: Long? = null): ParameterValues {
        val parameters = mutableMapOf<String, ParameterValue>("N" to ParameterValue.Integer(2), "S" to ParameterValue.Integer(1))
        if (seed != null) parameters["seed"] = ParameterValue.Integer(seed)
        return ParameterValues(parameters)
    }

    @Test fun seedChangesMeasuredStateAndRemainsDeterministic() {
        fun state(seed: Long): FloatArray {
            val gpu = GpuStatusSink { }
            val run = algorithm.prepare(RunRequest("nbody", ExecutionMode.CPU_SINGLE, values(seed)), gpu)
            run.execute(ProgressSink { }, gpu)
            run.checkAndSummarize()
            val positions = run.finalStateCopy().positionMass
            run.close()
            return positions
        }
        assertContentEquals(state(0), state(0))
        assertFalse(state(0).contentEquals(state(42)))
    }

    @Test fun seedLimitsAndDefaultComparison() {
        for (seed in listOf(0L, 42L, 4294967295L)) {
            assertTrue(algorithm.validate(values(seed)) is ParameterValidation.Valid)
            assertEquals(seed.toString(), algorithm.describe(values(seed)).numericalSettings["seed"])
        }
        for (seed in listOf(-1L, 4294967296L)) {
            assertTrue(algorithm.validate(values(seed)) is ParameterValidation.Invalid)
        }
        assertEquals(algorithm.describe(values()).key, algorithm.describe(values(42)).key)
        assertNotEquals(algorithm.describe(values(0)).key, algorithm.describe(values(42)).key)
    }
}
