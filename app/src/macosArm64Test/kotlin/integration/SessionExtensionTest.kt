package integration

import dev.macmetaldemo.application.*
import dev.macmetaldemo.cpu.DefaultCpuRuntimeFactory
import dev.macmetaldemo.domain.*
import dev.macmetaldemo.nbody.NBodyAlgorithm
import dev.macmetaldemo.terminal.*
import kotlin.test.*
import integration.fixtures.SampleAlgorithm

private class TrackedNBody(private val delegate: DemoAlgorithm) : DemoAlgorithm by delegate {
    val runs = mutableListOf<PreparedRun>()
    override fun prepare(request: RunRequest, gpu: GpuStatusSink): PreparedRun {
        val run = delegate.prepare(request, gpu)
        runs.add(run)
        return run
    }
}


class SessionExtensionTest {
    @Test fun registeredAlgorithmAndRepeatedNBodyRunsUseTheSameSessionAndController() {
        val nbody = TrackedNBody(NBodyAlgorithm(DefaultCpuRuntimeFactory(), dev.macmetaldemo.metal.DefaultMetalRuntimeFactory()))
        val sample = SampleAlgorithm()
        val registry = AlgorithmRegistry(listOf(nbody, sample))
        val history = RunHistory(registry)
        var now = 0L
        val controller = RunController(registry, MonotonicClock { now += 1_000_000; now })
        val inputs = listOf("1", "1", "2", "1", "", "2", "1", "5", "1.5", "1", "1", "", "", "", "2", "1", "", "", "0").iterator()
        var output = ""
        val io = object : TerminalIo {
            override fun readLine(): String? = if (inputs.hasNext()) inputs.next() else null
            override fun write(text: String) { output += text }
        }
        val outcomes = mutableListOf<RunOutcome>()
        val driver = RunDriver { request -> controller.run(request, RunSnapshotSink { }).also { outcomes.add(it) } }
        assertEquals(0, TerminalSession(registry, driver, history, io).run())
        assertEquals(4, outcomes.size)
        assertTrue(outcomes.all { it is RunOutcome.Success })
        assertEquals(2, nbody.runs.size)
        assertNotSame(nbody.runs[0], nbody.runs[1])
        val results = outcomes.map { (it as RunOutcome.Success).result }
        assertEquals("N=2,S=1,seed=42", results[0].descriptor.key.canonicalParameters)
        assertEquals("items=5,gain=1.5", results[1].descriptor.key.canonicalParameters)
        assertEquals("N=2,S=1,seed=42", results[2].descriptor.key.canonicalParameters)
        assertEquals("items=5,gain=1.5", results[3].descriptor.key.canonicalParameters)
        assertTrue(output.contains("2  Sample - sample work"))
        assertTrue(output.contains("items [3]"))
        assertTrue(output.contains("gain [1.0]"))
        assertTrue(output.contains("batches"))
        assertTrue(output.contains("samples"))
        assertTrue(output.contains("sample result"))
        assertEquals(1.0, history.speedIncrease(results[2]))
        assertEquals(1.0, history.speedIncrease(results[3]))
    }
    @Test fun showsPreparedBackendBeforeMeasuredExecution() {
        var output = ""
        var reachedExecute = false
        val algorithm = SampleAlgorithm {
            reachedExecute = true
            val mode = output.indexOf("Mode: CPU - one thread\n")
            val preparation = output.indexOf("Preparing.\n")
            val backend = output.indexOf("CPU workers: 1\n")
            val warmup = output.indexOf("Warming up.\n")
            assertTrue(mode >= 0 && mode < preparation)
            assertTrue(preparation < backend && backend < warmup)
        }
        val registry = AlgorithmRegistry(listOf(algorithm))
        var now = 0L
        val controller = RunController(registry, MonotonicClock { now += 1_000; now })
        val input = listOf("1", "1", "", "", "0").iterator()
        val io = object : TerminalIo {
            override fun readLine(): String? = if (input.hasNext()) input.next() else null
            override fun write(text: String) { output += text }
        }
        val renderer = RunSnapshotRenderer(io)
        val outcomes = mutableListOf<RunOutcome>()
        val driver = RunDriver { request ->
            controller.run(request, RunSnapshotSink { renderer.render(it) }).also { outcomes.add(it) }
        }
        assertEquals(0, TerminalSession(registry, driver, RunHistory(registry), io).run())
        assertTrue(reachedExecute)
        assertTrue(outcomes.single() is RunOutcome.Success)
    }
    @Test fun liveSessionPrintsOneFinalResult() {
        val sample = SampleAlgorithm()
        val registry = AlgorithmRegistry(listOf(sample))
        val clock = MonotonicClock { 1L }
        val controller = RunController(registry, clock)
        val input = listOf("1", "1", "", "", "0").iterator()
        var output = ""
        val io = object : TerminalIo {
            override fun readLine(): String? = if (input.hasNext()) input.next() else null
            override fun write(text: String) { output += text }
        }
        val worker = RunWorker { request ->
            val outcome = controller.run(request) { }
            object : RunHandle {
                override fun latest() = RunSnapshot(RunPhase.SUCCEEDED, null, null, null, null, null, null, null)
                override fun outcome() = outcome
                override fun awaitCompletion() = outcome
            }
        }
        val driver = LiveRunDriver(worker, clock, ResourceSampler { ResourceSnapshot(1, emptyList(), null, null) },
            WaitStrategy { }, io, { TerminalCapabilities(false, null, 80, 24, false) })
        assertEquals(0, TerminalSession(registry, driver, RunHistory(registry), io).run())
        assertEquals(1, Regex("Calculation:").findAll(output).count())
        assertEquals(1, Regex("sample result").findAll(output).count())
    }

}
