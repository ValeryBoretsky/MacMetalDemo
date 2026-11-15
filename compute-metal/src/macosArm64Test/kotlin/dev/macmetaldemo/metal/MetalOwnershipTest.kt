package dev.macmetaldemo.metal

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.*
import dev.macmetaldemo.domain.GpuCommandState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class MetalOwnershipTest {
    @Test
    fun successiveDispatchesKeepOnlyMeasuredDurationThroughController() {
        val native = FakeMetalCalls(start = 1.0, end = 2.0)
        val program = DefaultMetalRuntimeFactory(native).open("source", "entry")
        val values = ParameterValues(emptyMap())
        val algorithm = object : DemoAlgorithm {
            override val info = AlgorithmInfo("test", "1", "Test", "Test")
            override val parameters = emptyList<ParameterDefinition>()
            override val supportedModes = setOf(ExecutionMode.METAL)
            override fun availability(mode: ExecutionMode) = ModeAvailability(mode, true, null)
            override fun validate(values: ParameterValues): ParameterValidation = ParameterValidation.Valid(values)
            override fun describe(values: ParameterValues) = RunDescriptor(
                ComparisonKey("test", "1", "", "", "work"), values, emptyMap(),
                WorkDefinition("work", "units", 1.0, "units/s"), 1, 1, "unit", emptyList())
            override fun prepare(request: RunRequest, gpu: GpuStatusSink) = object : PreparedRun {
                override val backend = BackendInfo(null, "Test GPU")
                override fun warmUp() {
                    native.start = 1.0; native.end = 2.0
                    program.dispatch(emptyList(), byteArrayOf(), 1, gpu)
                }
                override fun execute(progress: ProgressSink, gpu: GpuStatusSink) {
                    native.start = 1.0; native.end = 1.5
                    program.dispatch(emptyList(), byteArrayOf(), 1, gpu)
                    native.start = 2.0; native.end = 3.0
                    program.dispatch(emptyList(), byteArrayOf(), 1, gpu)
                    native.start = 4.0; native.end = 3.0
                    program.dispatch(emptyList(), byteArrayOf(), 1, gpu)
                    progress.publish(RunProgress(1, 1, 1))
                }
                override fun checkAndSummarize() = CheckedSummary(emptyList())
                override fun close() = Unit
            }
        }
        val controller = RunController(AlgorithmRegistry(listOf(algorithm)), MonotonicClock { 1L })
        repeat(2) {
            val snapshots = mutableListOf<RunSnapshot>()
            val outcome = controller.run(RunRequest("test", ExecutionMode.METAL, values)) { snapshots.add(it) }
            kotlin.test.assertIs<RunOutcome.Success>(outcome)
            val submitted = snapshots.filter { it.phase == RunPhase.RUNNING && it.gpu?.state == GpuCommandState.SUBMITTED }
            assertEquals(listOf(null, 500_000_000L, 1_000_000_000L), submitted.map { it.gpu?.lastDurationNs })
            val completed = snapshots.filter { it.phase == RunPhase.RUNNING && it.gpu?.state == GpuCommandState.COMPLETED }
            assertNull(completed.last().gpu?.lastDurationNs)
        }
        program.close()
    }

    @Test
    fun failedStatusDoesNotReadTimestamps() {
        val operations = FakeCommandOperations(completed = false)
        assertFails { executeMetalCommand(operations) {} }
        assertEquals(listOf("commit", "wait", "status", "error"), operations.events)
    }

    @Test
    fun errorStatusDoesNotReadTimestamps() {
        val operations = FakeCommandOperations(error = "command error")
        assertFails { executeMetalCommand(operations) {} }
        assertEquals(listOf("commit", "wait", "status", "error"), operations.events)
    }

    @Test
    fun submittedCallbackFailureWaitsBeforeCleanupAndPreservesFailure() {
        val operations = FakeCommandOperations(waitFails = true)
        val native = FakeMetalCalls(operations = operations)
        val program = DefaultMetalRuntimeFactory(native).open("source", "entry")
        val primary = IllegalStateException("sink failure")
        val caught = assertFails {
            program.dispatch(emptyList(), byteArrayOf(), 1, GpuStatusSink {
                if (it.state == GpuCommandState.SUBMITTED) throw primary
            })
        }
        assertSame(primary, caught)
        assertEquals("wait failure", caught.suppressedExceptions.single().message)
        assertEquals(listOf("commit", "wait", "close"), operations.events)
        program.close()
    }

    @Test
    fun submittedCallbackFailureWaitsForCompletionBeforeClose() {
        val operations = FakeCommandOperations()
        val program = DefaultMetalRuntimeFactory(FakeMetalCalls(operations = operations)).open("source", "entry")
        val primary = IllegalStateException("sink failure")
        val caught = assertFails {
            program.dispatch(emptyList(), byteArrayOf(), 1, GpuStatusSink {
                if (it.state == GpuCommandState.SUBMITTED) throw primary
            })
        }
        assertSame(primary, caught)
        assertEquals(listOf("commit", "wait", "close"), operations.events)
        program.close()
    }

    @Test
    fun successfulCommandReadsTimestampsAfterStatusAndError() {
        val operations = FakeCommandOperations()
        executeMetalCommand(operations) { operations.events.add("submitted") }
        assertEquals(listOf("commit", "submitted", "wait", "status", "error", "start", "end"), operations.events)
    }

    @Test
    fun failureBeforeCommitDoesNotPublishSubmitted() {
        val native = FakeMetalCalls(failure = "execute")
        val program = DefaultMetalRuntimeFactory(native).open("source", "entry")
        val states = mutableListOf<GpuCommandState>()
        assertFails { program.dispatch(emptyList(), byteArrayOf(), 1, GpuStatusSink { states.add(it.state) }) }
        assertEquals(listOf(GpuCommandState.PREPARING), states)
        program.close()
    }
    @Test
    fun partialProgramAcquisitionClosesEarlierHandles() {
        for (failure in listOf("queue", "library", "pipeline")) {
            val native = FakeMetalCalls(failure)
            assertFails { DefaultMetalRuntimeFactory(native).open("source", "entry") }
            assertEquals(native.acquired.sorted(), native.closed.sorted())
        }
    }

    @Test
    fun bufferAndCommandFailuresCloseAcquiredHandles() {
        for (failure in listOf("buffer", "command")) {
            val native = FakeMetalCalls(failure)
            val program = DefaultMetalRuntimeFactory(native).open("source", "entry")
            if (failure == "buffer") assertFails { program.createBuffer(4) }
            else {
                val buffer = program.createBuffer(4)
                assertFails { program.dispatch(listOf(buffer), byteArrayOf(), 1, GpuStatusSink {}) }
            }
            program.close()
            program.close()
            assertEquals(native.acquired.sorted(), native.closed.sorted())
        }
    }

    @Test
    fun rejectsInvalidBufferLengthsBeforeAllocation() {
        val native = FakeMetalCalls()
        val program = DefaultMetalRuntimeFactory(native).open("source", "entry")
        for (size in listOf(0L, -1L, Long.MAX_VALUE, 1025L)) {
            assertFails { program.createBuffer(size) }
        }
        assertFalse(native.acquired.contains("buffer"))
        program.close()
    }

    @Test
    fun failedCommandNeverPublishesCompletion() {
        val native = FakeMetalCalls(commandFails = true)
        val program = DefaultMetalRuntimeFactory(native).open("source", "entry")
        val states = mutableListOf<GpuCommandState>()
        assertFails { program.dispatch(emptyList(), byteArrayOf(), 1, GpuStatusSink { states.add(it.state) }) }
        assertFalse(states.contains(GpuCommandState.COMPLETED))
        program.close()
        assertEquals(native.acquired.sorted(), native.closed.sorted())
    }

    @Test
    fun closesOutstandingBufferOnlyOnce() {
        val native = FakeMetalCalls()
        val program = DefaultMetalRuntimeFactory(native).open("source", "entry")
        val buffer = program.createBuffer(4)
        program.close()
        buffer.close()
        program.close()
        assertEquals(native.acquired.sorted(), native.closed.sorted())
    }

    @Test
    fun invalidTimestampsAreUnavailable() {
        val native = FakeMetalCalls(start = 4.0, end = 3.0)
        val program = DefaultMetalRuntimeFactory(native).open("source", "entry")
        assertNull(program.dispatch(emptyList(), byteArrayOf(), 1, GpuStatusSink {}))
        program.close()
    }

    @Test
    fun completedTimestampIntervalConvertsToNanoseconds() {
        val program = DefaultMetalRuntimeFactory(FakeMetalCalls(start = 1.0, end = 1.5)).open("source", "entry")
        val durations = mutableListOf<Long?>()
        assertEquals(500_000_000L, program.dispatch(emptyList(), byteArrayOf(), 1, GpuStatusSink { durations.add(it.lastDurationNs) }))
        assertEquals(listOf(null, null, 500_000_000L), durations)
        program.close()
    }

    @Test
    fun completedStatusWithErrorDoesNotPublishCompletion() {
        val native = FakeMetalCalls(completionError = "command error")
        val program = DefaultMetalRuntimeFactory(native).open("source", "entry")
        val states = mutableListOf<GpuCommandState>()
        assertFails { program.dispatch(emptyList(), byteArrayOf(), 1, GpuStatusSink { states.add(it.state) }) }
        assertFalse(states.contains(GpuCommandState.COMPLETED))
        program.close()
    }
}

private class FakeCommandOperations(
    private val completed: Boolean = true,
    private val error: String? = null,
    private val waitFails: Boolean = false,
    private val commitFails: Boolean = false,
    private val startTime: Double = 1.0,
    private val endTime: Double = 2.0,
) : CommandOperations {
    val events = mutableListOf<String>()
    override fun commit() { events.add("commit"); if (commitFails) error("encoder failure") }
    override fun waitForCompletion() { events.add("wait"); if (waitFails) error("wait failure") }
    override fun completed(): Boolean { events.add("status"); return completed }
    override fun error(): String? { events.add("error"); return error }
    override fun start(): Double { events.add("start"); return startTime }
    override fun end(): Double { events.add("end"); return endTime }
}

private class FakeMetalCalls(
    private val failure: String? = null,
    private val commandFails: Boolean = false,
    var start: Double = 1.0,
    var end: Double = 2.0,
    private val completionError: String? = null,
    private val operations: FakeCommandOperations? = null,
) : MetalNativeCalls {
    val acquired = mutableListOf<String>()
    val closed = mutableListOf<String>()
    override val deviceName = "test device"
    override val maxBufferBytes = 1024L
    override fun queue(): NativeHandle = handle("queue")
    override fun library(source: String): NativeHandle = handle("library")
    override fun pipeline(library: NativeHandle, entry: String): NativeHandle = handle("pipeline")
    override fun buffer(size: Long): NativeBuffer {
        val owner = handle("buffer")
        return object : NativeBuffer {
            override fun write(values: FloatArray) = Unit
            override fun readInto(values: FloatArray) = Unit
            override fun close() = owner.close()
        }
    }
    override fun command(queue: NativeHandle, pipeline: NativeHandle, buffers: List<NativeBuffer>, constants: ByteArray, count: Int): NativeCommand {
        val owner = handle("command")
        return object : NativeCommand {
            override fun execute(submitted: () -> Unit): NativeCompletion {
                val calls = operations ?: FakeCommandOperations(
                    completed = !commandFails,
                    error = if (commandFails) "test failure" else completionError,
                    commitFails = failure == "execute",
                    startTime = start,
                    endTime = end,
                )
                return executeMetalCommand(calls, submitted)
            }
            override fun close() { operations?.events?.add("close"); owner.close() }
        }
    }
    private fun handle(name: String): NativeHandle {
        if (failure == name) error("injected $name failure")
        acquired.add(name)
        return object : NativeHandle { override fun close() { closed.add(name) } }
    }
}
