package integration

import dev.macmetaldemo.app.NativeRunWorker
import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.*
import dev.macmetaldemo.monitoring.MacosClock
import dev.macmetaldemo.terminal.*
import platform.Foundation.NSCondition
import platform.Foundation.NSDate
import platform.posix.usleep
import kotlin.test.*

class ContextOrderingTest {
    private class Fixture : DemoAlgorithm {
        val condition = NSCondition()
        var contextShown = false
        var warmed = false
        var warmedBeforeContext = false
        var executed = false
        var closed = false
        override val info = AlgorithmInfo("context", "1", "Context", "test work")
        override val parameters = emptyList<ParameterDefinition>()
        override val supportedModes = setOf(ExecutionMode.CPU_SINGLE)
        override fun availability(mode: ExecutionMode) = ModeAvailability(mode, true, null)
        override fun validate(values: ParameterValues) = ParameterValidation.Valid(values)
        override fun describe(values: ParameterValues) = RunDescriptor(
            ComparisonKey("context", "1", "", "", "work"), values, emptyMap(),
            WorkDefinition("work", "units", 1.0, "units/s"), 1, 1, "units", emptyList())
        override fun prepare(request: RunRequest, gpu: GpuStatusSink) = object : PreparedRun {
            override val backend = BackendInfo(1, null)
            override fun warmUp() {
                condition.lock()
                warmed = true
                warmedBeforeContext = !contextShown
                condition.broadcast()
                condition.unlock()
            }
            override fun execute(progress: ProgressSink, gpu: GpuStatusSink) {
                executed = true
                progress.publish(RunProgress(1, 1, 1))
            }
            override fun checkAndSummarize() = CheckedSummary(emptyList())
            override fun close() { closed = true }
        }
    }
    private val request = RunRequest("context", ExecutionMode.CPU_SINGLE, ParameterValues(emptyMap()))
    private fun driver(fixture: Fixture, io: TerminalIo, sampler: ResourceSampler): LiveRunDriver {
        val clock = MacosClock()
        return LiveRunDriver(NativeRunWorker(RunController(AlgorithmRegistry(listOf(fixture)), clock)), clock, sampler,
            WaitStrategy { usleep((it / 1_000).coerceAtLeast(1).toUInt()) }, io,
            { TerminalCapabilities(false, null, 80, 24, false) })
    }
    @Test fun backendOutputPrecedesWarmupAndMeasuredExecution() {
        val fixture = Fixture()
        var firstWrite = true
        val io = object : TerminalIo {
            override fun readLine(): String? = null
            override fun write(text: String) {
                fixture.condition.lock()
                if (firstWrite) {
                    firstWrite = false
                    val deadline = NSDate(timeIntervalSinceReferenceDate = NSDate().timeIntervalSinceReferenceDate + 1.0)
                    while (!fixture.warmed && fixture.condition.waitUntilDate(deadline)) { }
                }
                if (text.contains("CPU workers: 1")) fixture.contextShown = true
                fixture.condition.unlock()
            }
        }
        val result = driver(fixture, io, ResourceSampler { ResourceSnapshot(0, emptyList(), null, null) }).run(request)
        assertIs<RunOutcome.Success>(result)
        assertTrue(fixture.contextShown)
        assertTrue(fixture.warmed)
        assertFalse(fixture.warmedBeforeContext)
        assertTrue(fixture.executed)
        assertTrue(fixture.closed)
    }
    @Test fun outputFailureReleasesContextWaitAndClosesResources() {
        val fixture = Fixture()
        var samplerClosed = false
        val sampler = object : ResourceSampler, AutoCloseable {
            override fun sample() = ResourceSnapshot(0, emptyList(), null, null)
            override fun close() { samplerClosed = true }
        }
        val io = object : TerminalIo {
            override fun readLine(): String? = null
            override fun write(text: String) { if (text.contains("CPU workers: 1")) throw IllegalStateException("output failed") }
        }
        assertFailsWith<IllegalStateException> { driver(fixture, io, sampler).run(request) }
        assertFalse(fixture.warmed)
        assertFalse(fixture.executed)
        assertTrue(fixture.closed)
        assertTrue(samplerClosed)
    }
}
