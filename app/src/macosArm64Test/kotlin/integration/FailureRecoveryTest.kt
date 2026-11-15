package integration

import dev.macmetaldemo.application.*
import dev.macmetaldemo.cpu.DefaultCpuRuntimeFactory
import dev.macmetaldemo.domain.*
import dev.macmetaldemo.metal.DefaultMetalRuntimeFactory
import dev.macmetaldemo.nbody.NBodyAlgorithm
import dev.macmetaldemo.terminal.*
import integration.fixtures.SampleAlgorithm
import kotlin.test.*

class FailureRecoveryTest {
    @Test fun unavailableMetalDoesNotOpenCpuRuntime() {
        var cpuOpens = 0
        val cpu = object : CpuRuntimeFactory {
            override fun open(mode: ExecutionMode, elementCount: Int): CpuRuntime {
                cpuOpens++
                error("Unexpected CPU runtime")
            }
        }
        val metal = object : MetalRuntimeFactory {
            override fun availability() = ModeAvailability(ExecutionMode.METAL, false, "Metal unavailable")
            override fun open(source: String, entryPoint: String): MetalProgram = error("Unexpected Metal runtime")
        }
        val algorithm = NBodyAlgorithm(cpu, metal)
        val values = ParameterValues(mapOf("N" to ParameterValue.Integer(2), "S" to ParameterValue.Integer(1)))
        val outcome = RunController(AlgorithmRegistry(listOf(algorithm)), MonotonicClock { 0L })
            .run(RunRequest("nbody", ExecutionMode.METAL, values)) {}
        assertEquals("Metal unavailable", assertIs<RunOutcome.Failure>(outcome).message)
        assertEquals(0, cpuOpens)
    }

    @Test fun failureLeavesOtherAlgorithmValuesAndBaselineAvailable() {
        val nbody = NBodyAlgorithm(DefaultCpuRuntimeFactory(), DefaultMetalRuntimeFactory())
        val sample = SampleAlgorithm { throw RunFailure("Sample failed") }
        val registry = AlgorithmRegistry(listOf(nbody, sample))
        val history = RunHistory(registry)
        var now = 0L
        val controller = RunController(registry, MonotonicClock { now += 1_000_000; now })
        val outcomes = mutableListOf<RunOutcome>()
        var preservedBaseline: Double? = null
        val driver = RunDriver { request ->
            controller.run(request) {}.also { outcome ->
                outcomes.add(outcome)
                if (outcome is RunOutcome.Failure) {
                    val first = (outcomes.first() as RunOutcome.Success).result
                    preservedBaseline = history.speedIncrease(first.copy(request = first.request.copy(mode = ExecutionMode.METAL)))
                }
            }
        }
        val input = listOf("1", "1", "2", "1", "", "2", "3", "1", "5", "1.5", "1", "1", "", "", "", "0").iterator()
        var output = ""
        val io = object : TerminalIo {
            override fun readLine() = if (input.hasNext()) input.next() else null
            override fun write(text: String) { output += text }
        }
        assertEquals(0, TerminalSession(registry, driver, history, io).run())
        assertEquals(listOf("nbody", "sample"), registry.entries.map { it.info.id })
        assertEquals(3, outcomes.size)
        val first = assertIs<RunOutcome.Success>(outcomes[0]).result
        assertIs<RunOutcome.Failure>(outcomes[1])
        val last = assertIs<RunOutcome.Success>(outcomes[2]).result
        assertEquals("N=2,S=1,seed=42", first.descriptor.key.canonicalParameters)
        assertEquals(first.descriptor.key, last.descriptor.key)
        assertEquals(1.0, preservedBaseline)
        assertEquals(1.0, history.speedIncrease(last))
        assertTrue(output.contains("Mode is not supported."))
        assertTrue(output.contains("Select an available mode."))
        assertTrue(output.contains("Sample failed"))
        assertTrue(output.contains("2  Sample - sample work"))
    }
}
