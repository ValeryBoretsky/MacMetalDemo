package integration

import dev.macmetaldemo.app.NativeRunWorker
import dev.macmetaldemo.application.*
import dev.macmetaldemo.cpu.OperationQueueCpuRuntime
import dev.macmetaldemo.monitoring.MacosClock
import dev.macmetaldemo.terminal.*
import platform.posix.usleep
import dev.macmetaldemo.domain.*
import platform.Foundation.NSCondition
import platform.Foundation.NSThread
import platform.Foundation.NSDate
import kotlin.test.*

class LiveSessionTest {
    @Test fun coordinatorRunsWithoutBlockingTheSessionAndPublishesOutcome() {
        val condition = NSCondition()
        var entered = false
        var released = false
        val caller = NSThread.currentThread
        var coordinator: NSThread? = null
        var rangeThread: NSThread? = null
        val algorithm = object : DemoAlgorithm {
            override val info = AlgorithmInfo("held", "1", "Held", "held work")
            override val parameters = emptyList<ParameterDefinition>()
            override val supportedModes = setOf(ExecutionMode.CPU_SINGLE)
            override fun availability(mode: ExecutionMode) = ModeAvailability(mode, true, null)
            override fun validate(values: ParameterValues) = ParameterValidation.Valid(values)
            override fun describe(values: ParameterValues) = RunDescriptor(
                ComparisonKey("held", "1", "", "", "work"), values, emptyMap(),
                WorkDefinition("work", "units", 1.0, "units/s"), 1, 1, "units", emptyList())
            override fun prepare(request: RunRequest, gpu: GpuStatusSink) = object : PreparedRun {
                override val backend = BackendInfo(1, null)
                override fun warmUp() = Unit
                override fun execute(progress: ProgressSink, gpu: GpuStatusSink) {
                    condition.lock()
                    coordinator = NSThread.currentThread
                    entered = true
                    condition.signal()
                    while (!released) {
                        if (!condition.waitUntilDate(NSDate(timeIntervalSinceReferenceDate = NSDate().timeIntervalSinceReferenceDate + 5.0))) break
                    }
                    condition.unlock()
                    val runtime = OperationQueueCpuRuntime(1)
                    try { runtime.forEachRange(1) { _, _ -> rangeThread = NSThread.currentThread } }
                    finally { runtime.close() }
                    progress.publish(RunProgress(1, 1, 1))
                }
                override fun checkAndSummarize() = CheckedSummary(emptyList())
                override fun close() = Unit
            }
        }
        val controller = RunController(AlgorithmRegistry(listOf(algorithm)), MonotonicClock { 1L })
        val handle = NativeRunWorker(controller).start(RunRequest("held", ExecutionMode.CPU_SINGLE, ParameterValues(emptyMap())))
        condition.lock()
        while (!entered) {
            assertTrue(condition.waitUntilDate(NSDate(timeIntervalSinceReferenceDate = NSDate().timeIntervalSinceReferenceDate + 5.0)))
        }
        assertNull(handle.outcome())
        assertEquals(0L, handle.latest().progress?.completedUnits)
        assertNotEquals(caller, coordinator)
        condition.unlock()
        var samples = 0
        var output = ""
        var wrongWriter = false
        val clock = MacosClock()
        val io = object : TerminalIo {
            override fun readLine(): String? = null
            override fun write(text: String) {
                if (NSThread.currentThread != caller) wrongWriter = true
                output += text
            }
        }
        val driver = LiveRunDriver(RunWorker { handle }, clock, ResourceSampler {
            samples++
            if (samples == 3) {
                condition.lock()
                released = true
                condition.signal()
                condition.unlock()
            }
            ResourceSnapshot(clock.nowNs(), listOf(CoreLoad(0, 50.0)), 20.0, null)
        }, WaitStrategy { usleep((it / 1_000).toUInt()) }, io,
            { TerminalCapabilities(false, null, 80, 24, false) })
        val result = driver.run(RunRequest("held", ExecutionMode.CPU_SINGLE, ParameterValues(emptyMap())))
        assertIs<RunOutcome.Success>(result)
        assertTrue(samples >= 3)
        assertTrue(output.contains("0/1"))
        assertTrue(output.contains("System CPU 0"))
        assertFalse(wrongWriter)
        assertTrue(output.contains("Preparing."))
        assertTrue(output.contains("Warming up."))
        assertTrue(output.contains("CPU workers: 1"))
        assertNotNull(rangeThread)
        assertNotEquals(coordinator, rangeThread)
    }
    @Test fun coordinatorPublishesFailureWhenFailurePublicationThrows() {
        var calls = 0
        var closed = false
        val clock = MonotonicClock {
            calls++
            if (calls >= 3) throw IllegalStateException("failure publication clock failed")
            1L
        }
        val algorithm = object : DemoAlgorithm {
            override val info = AlgorithmInfo("failing", "1", "Failing", "failing work")
            override val parameters = emptyList<ParameterDefinition>()
            override val supportedModes = setOf(ExecutionMode.CPU_SINGLE)
            override fun availability(mode: ExecutionMode) = ModeAvailability(mode, true, null)
            override fun validate(values: ParameterValues) = ParameterValidation.Valid(values)
            override fun describe(values: ParameterValues) = RunDescriptor(
                ComparisonKey("failing", "1", "", "", "work"), values, emptyMap(),
                WorkDefinition("work", "units", 1.0, "units/s"), 1, 1, "units", emptyList())
            override fun prepare(request: RunRequest, gpu: GpuStatusSink) = object : PreparedRun {
                override val backend = BackendInfo(1, null)
                override fun warmUp() = Unit
                override fun execute(progress: ProgressSink, gpu: GpuStatusSink) { throw RunFailure("execute failed") }
                override fun checkAndSummarize() = CheckedSummary(emptyList())
                override fun close() { closed = true }
            }
        }
        val worker = NativeRunWorker(RunController(AlgorithmRegistry(listOf(algorithm)), clock))
        val handle = worker.start(RunRequest("failing", ExecutionMode.CPU_SINGLE, ParameterValues(emptyMap())))
        val deadline = NSDate().timeIntervalSinceReferenceDate + 5.0
        while (handle.outcome() == null && NSDate().timeIntervalSinceReferenceDate < deadline) usleep(1_000u)
        val outcome = assertIs<RunOutcome.Failure>(handle.outcome())
        assertEquals("failure publication clock failed", outcome.message)
        assertTrue(outcome.fatal)
        assertTrue(closed)
        assertEquals(outcome, handle.awaitCompletion())
    }

}
