package dev.macmetaldemo.application

import dev.macmetaldemo.domain.*
import kotlin.test.*

class AlgorithmRegistryTest {
    private fun algorithm(id: String) = object : DemoAlgorithm {
        override val info = AlgorithmInfo(id, "1", id, id)
        override val parameters = emptyList<ParameterDefinition>()
        override val supportedModes = emptySet<ExecutionMode>()
        override fun availability(mode: ExecutionMode) = ModeAvailability(mode, false, "Unavailable")
        override fun validate(values: ParameterValues): ParameterValidation = ParameterValidation.Valid(values)
        override fun describe(values: ParameterValues): RunDescriptor = error("unused")
        override fun prepare(request: RunRequest, gpu: GpuStatusSink): PreparedRun = error("unused")
    }
    @Test fun duplicateAlgorithmIdsFail() { assertFailsWith<IllegalArgumentException> { AlgorithmRegistry(listOf(algorithm("a"), algorithm("a"))) } }
    @Test fun emptyIdFails() { assertFailsWith<IllegalArgumentException> { AlgorithmRegistry(listOf(algorithm(""))) } }
    @Test fun registryOrderDoesNotChangeIdentity() {
        val a = algorithm("a"); val b = algorithm("b")
        assertSame(a, AlgorithmRegistry(listOf(b, a)).find("a"))
        assertSame(b, AlgorithmRegistry(listOf(a, b)).find("b"))
    }
}
