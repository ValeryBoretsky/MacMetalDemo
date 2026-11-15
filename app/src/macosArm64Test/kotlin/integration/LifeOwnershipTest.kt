package integration

import dev.macmetaldemo.application.*
import dev.macmetaldemo.cpu.DefaultCpuRuntimeFactory
import dev.macmetaldemo.domain.*
import dev.macmetaldemo.life.GameOfLifeAlgorithm
import dev.macmetaldemo.metal.DefaultMetalRuntimeFactory
import dev.macmetaldemo.terminal.*
import kotlin.test.*

class LifeOwnershipTest {
    private class TrackedCpu : CpuRuntimeFactory {
        private val delegate = DefaultCpuRuntimeFactory()
        var opens = 0
        var closes = 0
        override fun open(mode: ExecutionMode, elementCount: Int): CpuRuntime {
            val runtime = delegate.open(mode, elementCount)
            opens++
            return object : CpuRuntime by runtime {
                override fun close() {
                    closes++
                    runtime.close()
                }
            }
        }
    }

    private class TrackedMetal(
        private val failAllocation: Int? = null,
        private val failDispatch: Int? = null,
        private val byteLimit: Long? = null,
        val failRead: Boolean = false,
        val failBufferClose: Boolean = false,
        private val failProgramClose: Boolean = false,
    ) : MetalRuntimeFactory {
        private val delegate = DefaultMetalRuntimeFactory()
        val buffers = mutableListOf<TrackedBuffer>()
        val reads = mutableListOf<Pair<Int, FloatArray>>()
        var programsOpened = 0
        var programsClosed = 0
        var allocations = 0
        var dispatchAttempts = 0
        var completedDispatches = 0
        val commandFailure = RunFailure("Injected Life command failure.")
        val readFailure = RunFailure("Injected Life read failure.")
        val programCloseFailure = RunFailure("Injected Life program cleanup failure.")

        override fun availability() = delegate.availability()
        override fun open(source: String, entryPoint: String): MetalProgram {
            val program = delegate.open(source, entryPoint)
            programsOpened++
            return object : MetalProgram by program {
                override val maxBufferBytes get() = byteLimit ?: program.maxBufferBytes
                override fun createBuffer(byteCount: Long): MetalBuffer {
                    allocations++
                    if (allocations == failAllocation) throw RunFailure("Injected Life allocation failure.")
                    return TrackedBuffer(program.createBuffer(byteCount), this@TrackedMetal).also { buffers.add(it) }
                }

                override fun dispatch(buffers: List<MetalBuffer>, constants: ByteArray,
                                      elementCount: Int, gpu: GpuStatusSink): Long? {
                    dispatchAttempts++
                    if (dispatchAttempts == failDispatch) throw commandFailure
                    val duration = program.dispatch(buffers.map { (it as TrackedBuffer).delegate }, constants, elementCount, gpu)
                    completedDispatches++
                    return duration
                }

                override fun close() {
                    programsClosed++
                    program.close()
                    if (failProgramClose) throw programCloseFailure
                }
            }
        }

        fun assertReleased() {
            assertEquals(programsOpened, programsClosed)
            assertTrue(programsOpened > 0)
            assertTrue(buffers.all { it.closes == 1 }, buffers.map { it.closes }.toString())
        }
    }

    private class TrackedBuffer(val delegate: MetalBuffer, private val owner: TrackedMetal) : MetalBuffer by delegate {
        var closes = 0
        val closeFailure = RunFailure("Injected Life buffer cleanup failure.")
        override fun readInto(values: FloatArray) {
            owner.reads.add(owner.completedDispatches to values)
            if (owner.failRead) throw owner.readFailure
            delegate.readInto(values)
        }
        override fun readRecords(indices: IntArray, recordSize: Int, values: FloatArray) {
            fail("Life must transfer the full board.")
        }
        override fun close() {
            closes++
            delegate.close()
            if (owner.failBufferClose) throw closeFailure
        }
    }

    private val gpu = GpuStatusSink { }
    private val values = lifeValues(5, 4, 7, 3)

    private fun request(mode: ExecutionMode = ExecutionMode.METAL, parameters: ParameterValues = values,
                        capture: Boolean = false) = RunRequest("game-of-life", mode, parameters, capture)

    @Test fun partialMeasuredAllocationReleasesEachAcquiredResource() {
        for (failedAllocation in 1..2) {
            val cpu = TrackedCpu()
            val metal = TrackedMetal(failAllocation = failedAllocation)
            val algorithm = GameOfLifeAlgorithm(cpu, metal)
            val outcome = RunController(AlgorithmRegistry(listOf(algorithm)), MonotonicClock { 0 })
                .run(request()) { }
            assertContains(assertIs<RunOutcome.Failure>(outcome).message, "Injected Life allocation failure.")
            assertEquals(failedAllocation, metal.allocations)
            assertEquals(failedAllocation - 1, metal.buffers.size)
            assertEquals(0, cpu.opens)
            metal.assertReleased()
        }
    }

    @Test fun partialWarmAllocationAlsoReleasesTheMeasuredBoards() {
        for (failedAllocation in 3..4) {
            val cpu = TrackedCpu()
            val metal = TrackedMetal(failAllocation = failedAllocation)
            val algorithm = GameOfLifeAlgorithm(cpu, metal)
            val outcome = RunController(AlgorithmRegistry(listOf(algorithm)), MonotonicClock { 0 })
                .run(request()) { }
            assertContains(assertIs<RunOutcome.Failure>(outcome).message, "Injected Life allocation failure.")
            assertEquals(failedAllocation, metal.allocations)
            assertEquals(listOf(80L, 80L), metal.buffers.take(2).map { it.byteCount })
            assertEquals(failedAllocation - 1, metal.buffers.size)
            assertEquals(0, cpu.opens)
            metal.assertReleased()
        }
    }

    @Test fun bothMeasuredAndWarmBufferLimitsFailBeforeAnyAllocation() {
        val cases = listOf(lifeValues(17, 19, 7, 1) to 1291L, lifeValues(5, 4, 7, 1) to 1023L)
        for ((parameters, limit) in cases) {
            val cpu = TrackedCpu()
            val metal = TrackedMetal(byteLimit = limit)
            val algorithm = GameOfLifeAlgorithm(cpu, metal)
            val outcome = RunController(AlgorithmRegistry(listOf(algorithm)), MonotonicClock { 0 })
                .run(request(parameters = parameters)) { }
            assertIs<RunOutcome.Failure>(outcome)
            assertEquals(0, metal.allocations, "The buffer limit must apply before allocation.")
            assertEquals(0, metal.dispatchAttempts)
            assertEquals(0, cpu.opens)
            metal.assertReleased()
        }
    }

    @Test fun failedMeasuredCommandGetsNoProgressCredit() {
        val cpu = TrackedCpu()
        val metal = TrackedMetal(failDispatch = 3)
        val run = GameOfLifeAlgorithm(cpu, metal).prepare(request(), gpu)
        val progress = mutableListOf<RunProgress>()
        try {
            run.warmUp()
            val error = assertFailsWith<RunFailure> { run.execute(ProgressSink { progress.add(it) }, gpu) }
            assertEquals("Injected Life command failure.", error.message)
            assertEquals(listOf(RunProgress(1, 3, 20)), progress)
            assertEquals(2, metal.completedDispatches)
            assertEquals(3, metal.dispatchAttempts)
            assertEquals(0, cpu.opens)
            metal.assertReleased()
        } finally {
            run.close()
        }
        metal.assertReleased()
    }

    @Test fun failedMeasuredRunRejectsExecutionBeforeTheCallerClosesIt() {
        val metal = TrackedMetal(failDispatch = 3)
        val run = GameOfLifeAlgorithm(TrackedCpu(), metal).prepare(request(), gpu)
        val progress = mutableListOf<RunProgress>()
        try {
            run.warmUp()
            assertSame(metal.commandFailure, assertFailsWith<RunFailure> {
                run.execute(ProgressSink { progress.add(it) }, gpu)
            })
            assertFailsWith<IllegalStateException> { run.execute(ProgressSink { progress.add(it) }, gpu) }
            assertEquals(listOf(RunProgress(1, 3, 20)), progress)
            assertEquals(3, metal.dispatchAttempts)
            assertFailsWith<IllegalStateException> { run.checkAndSummarize() }
            assertFailsWith<IllegalStateException> { run.finalBoardCopy() }
        } finally {
            run.close()
        }
        metal.assertReleased()
    }

    @Test fun warmFailureKeepsTheOriginalCauseAndEveryCleanupFailure() {
        val metal = TrackedMetal(failDispatch = 1, failBufferClose = true, failProgramClose = true)
        val run = GameOfLifeAlgorithm(TrackedCpu(), metal).prepare(request(), gpu)
        try {
            val error = assertFailsWith<RunFailure> { run.warmUp() }
            assertSame(metal.commandFailure, error)
            metal.assertReleased()
            val suppressed = mutableListOf<Throwable>()
            fun collect(cause: Throwable) {
                cause.suppressedExceptions.forEach {
                    suppressed.add(it)
                    collect(it)
                }
            }
            collect(error)
            for (buffer in metal.buffers) assertTrue(suppressed.any { it === buffer.closeFailure })
            assertTrue(suppressed.any { it === metal.programCloseFailure })
            assertFailsWith<IllegalStateException> { run.execute(ProgressSink { fail("Failed runs must not report progress.") }, gpu) }
        } finally {
            try { run.close() } catch (_: RunFailure) { }
        }
        metal.assertReleased()
    }

    @Test fun finalReadFailureClosesTheRunAndRejectsFurtherExecution() {
        val metal = TrackedMetal(failRead = true)
        val run = GameOfLifeAlgorithm(TrackedCpu(), metal).prepare(request(), gpu)
        try {
            run.warmUp()
            run.execute(ProgressSink { }, gpu)
            assertSame(metal.readFailure, assertFailsWith<RunFailure> { run.checkAndSummarize() })
            metal.assertReleased()
            assertFailsWith<IllegalStateException> { run.execute(ProgressSink { fail("Failed runs must not report progress.") }, gpu) }
            assertFailsWith<IllegalStateException> { run.checkAndSummarize() }
            assertFailsWith<IllegalStateException> { run.finalBoardCopy() }
            assertEquals(4, metal.dispatchAttempts)
            assertEquals(1, metal.reads.size)
        } finally {
            run.close()
        }
        metal.assertReleased()
    }

    @Test fun failedMetalRunPreservesHistoryAndAllowsTheNextCpuRun() {
        val cpu = TrackedCpu()
        val metal = TrackedMetal(failDispatch = 3)
        val algorithm = GameOfLifeAlgorithm(cpu, metal)
        val registry = AlgorithmRegistry(listOf(algorithm))
        val history = RunHistory(registry)
        var time = 0L
        val controller = RunController(registry, MonotonicClock { time += 1_000_000; time })
        val outcomes = mutableListOf<RunOutcome>()
        var baselineAfterFailure: Double? = null
        val inputs = listOf("1", "1", "5", "4", "7", "3", "42",
            "1", "3", "", "", "", "", "",
            "1", "1", "", "", "", "", "", "0").iterator()
        val io = object : TerminalIo {
            override fun readLine() = if (inputs.hasNext()) inputs.next() else null
            override fun write(text: String) = Unit
        }
        val driver = RunDriver { selected ->
            controller.run(selected) { }.also { outcome ->
                outcomes.add(outcome)
                if (outcome is RunOutcome.Failure) {
                    val baseline = assertIs<RunOutcome.Success>(outcomes.first()).result
                    baselineAfterFailure = history.speedIncrease(baseline)
                    assertEquals(1, cpu.opens, "A Metal failure must not start CPU work.")
                    metal.assertReleased()
                }
            }
        }
        assertEquals(0, TerminalSession(registry, driver, history, io).run())
        assertEquals(3, outcomes.size)
        val first = assertIs<RunOutcome.Success>(outcomes[0]).result
        assertIs<RunOutcome.Failure>(outcomes[1])
        val last = assertIs<RunOutcome.Success>(outcomes[2]).result
        assertEquals(first.descriptor.key, last.descriptor.key)
        assertEquals(first.summary.fields, last.summary.fields)
        assertEquals(1.0, baselineAfterFailure)
        assertEquals(2, cpu.opens)
        assertEquals(2, cpu.closes)
        metal.assertReleased()
    }

    @Test fun inactiveCaptureReadsOnlyDuringTheFinalCheck() {
        val metal = TrackedMetal()
        val run = GameOfLifeAlgorithm(TrackedCpu(), metal).prepare(request(capture = true), gpu)
        var polls = 0
        try {
            run.attachVisualization(object : VisualizationPort {
                override fun captureRequested(): Boolean { polls++; return false }
                override fun publish(frame: VisualizationFrame) = fail("Inactive capture must not publish a frame.")
                override fun disable() = fail("Inactive capture must not fail.")
            })
            run.warmUp()
            assertTrue(metal.reads.isEmpty())
            run.execute(ProgressSink { }, gpu)
            assertEquals(3, polls)
            assertTrue(metal.reads.isEmpty())
            run.checkAndSummarize()
            assertEquals(1, metal.reads.size)
            assertEquals(4, metal.reads.single().first)
            assertEquals(20, run.finalBoardCopy().size)
            assertEquals(1, metal.reads.size)
        } finally {
            run.close()
        }
        metal.assertReleased()
    }

    @Test fun eachRequestedFrameReadsOnceIntoTheSameHostArray() {
        val metal = TrackedMetal()
        val run = GameOfLifeAlgorithm(TrackedCpu(), metal).prepare(
            request(parameters = lifeValues(5, 4, 7, 4), capture = true), gpu)
        val frames = mutableListOf<VisualizationFrame>()
        var polls = 0
        try {
            run.attachVisualization(object : VisualizationPort {
                override fun captureRequested() = (++polls % 2) == 1
                override fun publish(frame: VisualizationFrame) { frames.add(frame) }
                override fun disable() = fail("Requested capture must not fail.")
            })
            run.warmUp()
            assertTrue(metal.reads.isEmpty())
            run.execute(ProgressSink { }, gpu)
            assertEquals(listOf(1L, 3L), frames.map { it.completedUnits })
            assertEquals(listOf(2, 4), metal.reads.map { it.first })
            assertSame(metal.reads[0].second, metal.reads[1].second)
            assertEquals(20, metal.reads[0].second.size)
            run.checkAndSummarize()
            assertEquals(3, metal.reads.size)
            assertEquals(5, metal.reads.last().first)
        } finally {
            run.close()
        }
        metal.assertReleased()
    }

    @Test fun drawingFailureDisablesFurtherCapturesWithoutChangingTheResult() {
        for (mode in ExecutionMode.entries) {
            val cpu = TrackedCpu()
            val metal = TrackedMetal()
            val run = GameOfLifeAlgorithm(cpu, metal).prepare(request(mode, capture = true), gpu)
            val progress = mutableListOf<RunProgress>()
            var polls = 0
            var publications = 0
            var disables = 0
            try {
                run.attachVisualization(object : VisualizationPort {
                    override fun captureRequested(): Boolean { polls++; return true }
                    override fun publish(frame: VisualizationFrame) { publications++; error("Injected drawing failure.") }
                    override fun disable() { disables++ }
                })
                run.warmUp()
                run.execute(ProgressSink { progress.add(it) }, gpu)
                run.checkAndSummarize()
                assertEquals(1, polls)
                assertEquals(1, publications)
                assertEquals(1, disables)
                assertEquals(listOf(RunProgress(1, 3, 20), RunProgress(2, 3, 40), RunProgress(3, 3, 60)), progress)
                assertContentEquals("00110111100011011110".map { if (it == '1') 1f else 0f }.toFloatArray(), run.finalBoardCopy())
                if (mode == ExecutionMode.METAL) assertEquals(2, metal.reads.size)
            } finally {
                run.close()
            }
            if (mode == ExecutionMode.METAL) metal.assertReleased() else assertEquals(cpu.opens, cpu.closes)
        }
    }

    @Test fun emptyBoardsStillCompleteEveryGenerationInEachMode() {
        for (mode in ExecutionMode.entries) {
            val cpu = TrackedCpu()
            val metal = TrackedMetal()
            val run = GameOfLifeAlgorithm(cpu, metal).prepare(request(mode, lifeValues(3, 3, 0, 3)), gpu)
            val progress = mutableListOf<RunProgress>()
            try {
                run.warmUp()
                run.execute(ProgressSink { progress.add(it) }, gpu)
                val summary = run.checkAndSummarize()
                assertEquals(listOf(RunProgress(1, 3, 9), RunProgress(2, 3, 18), RunProgress(3, 3, 27)), progress)
                assertEquals(ResultFieldValue("liveCells", "0"), summary.fields.first { it.id == "liveCells" })
                assertContentEquals(FloatArray(9), run.finalBoardCopy())
            } finally {
                run.close()
            }
            if (mode == ExecutionMode.METAL) metal.assertReleased() else assertEquals(cpu.opens, cpu.closes)
        }
    }
}
