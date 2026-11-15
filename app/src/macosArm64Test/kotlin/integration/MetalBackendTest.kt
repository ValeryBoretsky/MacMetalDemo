package integration

import dev.macmetaldemo.cpu.DefaultCpuRuntimeFactory
import dev.macmetaldemo.metal.DefaultMetalRuntimeFactory
import dev.macmetaldemo.domain.*
import dev.macmetaldemo.nbody.NBodyAlgorithm
import integration.fixtures.executeNBodyForTest
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.*

class MetalBackendTest {
    @Test fun metalMatchesCpuForAllComponents() {
        val algorithm = NBodyAlgorithm(DefaultCpuRuntimeFactory(), DefaultMetalRuntimeFactory())
        for (n in listOf(2, 17, 128, 257)) for (steps in listOf(1, 8)) {
            fun run(mode: ExecutionMode) = executeNBodyForTest(algorithm, RunRequest("nbody", mode,
                ParameterValues(mapOf("N" to ParameterValue.Integer(n.toLong()), "S" to ParameterValue.Integer(steps.toLong())))))
            val cpu = run(ExecutionMode.CPU_SINGLE)
            val gpu = run(ExecutionMode.METAL)
            val repeat = run(ExecutionMode.METAL)
            assertEquals(cpu.descriptor.exactWorkCount, gpu.descriptor.exactWorkCount)
            assertEquals(cpu.progress, gpu.progress)
            for (i in cpu.state.positionMass.indices) {
                for ((expected, actual) in listOf(cpu.state.positionMass[i] to gpu.state.positionMass[i], cpu.state.velocity[i] to gpu.state.velocity[i])) {
                    assertTrue(expected.isFinite() && actual.isFinite())
                    assertTrue(abs(actual - expected) <= 1e-4f + 1e-3f * max(abs(actual), abs(expected)), "N=$n S=$steps index=$i expected=$expected actual=$actual")
                }
                if (i % 4 == 3) {
                    assertEquals(cpu.state.positionMass[i], gpu.state.positionMass[i])
                    assertEquals(0f, gpu.state.velocity[i])
                }
            }
            assertContentEquals(gpu.state.positionMass, repeat.state.positionMass)
            assertContentEquals(gpu.state.velocity, repeat.state.velocity)
        }
    }
}
