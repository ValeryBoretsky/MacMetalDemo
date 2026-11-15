package integration

import dev.macmetaldemo.application.*
import dev.macmetaldemo.cpu.DefaultCpuRuntimeFactory
import dev.macmetaldemo.domain.*
import dev.macmetaldemo.metal.DefaultMetalRuntimeFactory
import dev.macmetaldemo.nbody.NBodyAlgorithm
import kotlin.test.*

class AlgorithmExtensionTest {
    @Test fun changedNBodyWorkloadDetailsCannotReuseBaseline() {
        val algorithm = NBodyAlgorithm(DefaultCpuRuntimeFactory(), DefaultMetalRuntimeFactory())
        val registry = AlgorithmRegistry(listOf(algorithm))
        val history = RunHistory(registry)
        val values = ParameterValues(mapOf("N" to ParameterValue.Integer(2), "S" to ParameterValue.Integer(1)))
        val request = RunRequest("nbody", ExecutionMode.CPU_SINGLE, values)
        var now = 0L
        val baseline = assertIs<RunOutcome.Success>(
            RunController(registry, MonotonicClock { now += 1_000_000; now }).run(request) {},
        ).result
        history.record(baseline)
        val key = baseline.descriptor.key
        val variants = listOf(
            key.copy(modelVersion = "other"),
            key.copy(canonicalParameters = "N=3,S=1"),
            key.copy(canonicalParameters = "N=2,S=2"),
            key.copy(numericalSettings = key.numericalSettings.replace("seed=42", "seed=43")),
            key.copy(numericalSettings = key.numericalSettings.replace("dt=0.001", "dt=0.002")),
            key.copy(workDefinitionId = "other"),
        )
        for (variant in variants) {
            assertNotEquals(key, variant)
            val result = baseline.copy(
                request = request.copy(mode = ExecutionMode.METAL),
                descriptor = RunDescriptor(variant, values, baseline.descriptor.numericalSettings, baseline.descriptor.work,
                    baseline.descriptor.exactWorkCount, baseline.descriptor.progressTotal,
                    baseline.descriptor.progressUnit, baseline.descriptor.resultFields),
            )
            assertNull(history.speedIncrease(result), variant.toString())
        }
    }
}
