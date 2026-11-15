package dev.macmetaldemo.terminal

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.*
import kotlin.test.*

class LiveRunDriverTest {
    private val request = RunRequest("test", ExecutionMode.CPU_SINGLE, ParameterValues(emptyMap()))
    private val snapshot = RunSnapshot(RunPhase.RUNNING, null, BackendInfo(1, null), RunProgress(0, 2, 0), 0, 0, null, null)
    private class Clock : MonotonicClock { var now = 0L; override fun nowNs() = now }
    private class Output : TerminalIo {
        var text = ""
        val writes = mutableListOf<String>()
        override fun readLine(): String? = null
        override fun write(text: String) { this.text += text; writes.add(text) }
    }
    private fun activeScreen(io: TerminalIo) = object : TerminalScreen {
        override val active = true
        override fun clear() = io.write("\u001b[2J\u001b[H")
        override fun close() = Unit
    }
    @Test fun directRunUsesParametersForVisualizationTitle() {
        val clock = Clock()
        val io = Output()
        val selected = request.copy(parameters = ParameterValues(mapOf("items" to ParameterValue.Integer(7))),
            visualizationEnabled = true)
        val descriptor = RunDescriptor(ComparisonKey("test", "1", "items=7", "", "work"),
            selected.parameters, emptyMap(), WorkDefinition("work", "unit", 1.0, "unit/s"),
            2, 2, "unit", emptyList())
        val handle = object : RunHandle {
            override fun latest() = snapshot.copy(descriptor = descriptor)
            override fun outcome() = if (clock.now >= 400_000_000L) RunOutcome.Failure("done", false) else null
            override fun awaitCompletion() = outcome()!!
            override fun latestVisualization() = VisualizationFrame(1, BooleanArray(80 * 60) { it == 0 })
        }
        LiveRunDriver(RunWorker { handle }, clock,
            ResourceSampler { ResourceSnapshot(clock.now, emptyList(), null, null) },
            WaitStrategy { clock.now += it }, io, { TerminalCapabilities(true, "xterm", 120, 32, false) },
            activeScreen(io), visualizationTitle = { id, values ->
                "$id ${(values["items"] as ParameterValue.Integer).value} bars"
            }).run(selected)
        assertContains(io.text, "test 7 bars")
    }

    private fun exercise(capabilities: (Long) -> TerminalCapabilities, active: Boolean = false, sample: (Long) -> ResourceSnapshot): Pair<Output, List<Long>> {
        val clock = Clock()
        val io = Output()
        val samples = mutableListOf<Long>()
        val handle = object : RunHandle {
            override fun latest() = snapshot
            override fun outcome(): RunOutcome? = if (clock.now >= 2_100_000_000L) RunOutcome.Failure("done", false) else null
            override fun awaitCompletion() = outcome()!!
        }
        val screen = if (active) activeScreen(io) else PlainTerminalScreen
        val driver = LiveRunDriver(RunWorker { handle }, clock, ResourceSampler {
            samples.add(clock.now)
            sample(clock.now)
        }, WaitStrategy { clock.now += it }, io, { capabilities(clock.now) }, screen)
        assertIs<RunOutcome.Failure>(driver.run(request))
        return io to samples
    }
    @Test fun requestsFramesOnlyDuringVisibleMeasuredRuns() {
        val clock = Clock()
        val io = Output()
        val times = mutableListOf<Long>()
        var titleLookups = 0
        val visibility = mutableListOf<Pair<Long, Boolean>>()
        val frame = VisualizationFrame(1, BooleanArray(80 * 60) { it == 0 })
        val descriptor = RunDescriptor(ComparisonKey("test", "1", "", "", "work"),
            request.parameters, emptyMap(), WorkDefinition("work", "unit", 1.0, "unit/s"),
            2, 2, "unit", emptyList())
        val handle = object : RunHandle {
            override fun latest() = snapshot.copy(descriptor = descriptor,
                phase = if (clock.now < 200_000_000L) RunPhase.WARMING_UP else RunPhase.RUNNING)
            override fun outcome(): RunOutcome? = if (clock.now >= 1_400_000_000L) RunOutcome.Failure("done", false) else null
            override fun awaitCompletion() = outcome()!!
            override fun setVisualizationVisible(visible: Boolean) { visibility.add(clock.now to visible) }
            override fun requestVisualization() { times.add(clock.now) }
            override fun latestVisualization() = frame
        }
        LiveRunDriver(RunWorker { handle }, clock,
            ResourceSampler { ResourceSnapshot(clock.now, (0 until 10).map { CoreLoad(it, 10.0) }, 10.0, null) },
            WaitStrategy { clock.now += it }, io,
            { TerminalCapabilities(true, "xterm", if (clock.now in 600_000_000L until 1_000_000_000L) 30 else 120, 32, false) },
            activeScreen(io), visualizationTitle = { _, _ -> titleLookups++; "Sorting bars" }).run(request.copy(visualizationEnabled = true))
        assertEquals(1, titleLookups)
        assertTrue(times.isNotEmpty())
        assertTrue(times.all { it >= 200_000_000L && it !in 600_000_000L until 1_000_000_000L })
        assertTrue(times.zipWithNext().all { (a, b) -> b - a >= 200_000_000L })
        assertTrue(visibility.any { (time, visible) -> time >= 600_000_000L && !visible })
        val picture = io.writes.first { it.contains("Sorting bars") }
        assertTrue(picture.lineSequence().first().contains("Algorithm: test"), picture)
        assertTrue(picture.contains("System CPU 9"), picture)
        assertTrue(picture.contains("*"))
    }

    @Test fun tenCorePictureFitsBelowCompactMeasurementsAt80By33() {
        val clock = Clock()
        val io = Output()
        val descriptor = RunDescriptor(ComparisonKey("test", "1", "N=4096,S=72", "FP32", "work"),
            request.parameters, emptyMap(), WorkDefinition("work", "units", 1.0, "units/s"),
            72, 72, "units", emptyList())
        val frame = VisualizationFrame(1, BooleanArray(80 * 60) { it == 0 })
        val handle = object : RunHandle {
            override fun latest() = snapshot.copy(descriptor = descriptor, progress = RunProgress(1, 72, 1))
            override fun outcome() = if (clock.now >= 400_000_000L) RunOutcome.Failure("done", false) else null
            override fun awaitCompletion() = outcome()!!
            override fun latestVisualization() = frame
        }
        LiveRunDriver(RunWorker { handle }, clock, ResourceSampler {
            ResourceSnapshot(clock.now, (0 until 10).map { CoreLoad(it, 10.0) }, 10.0, null)
        }, WaitStrategy { clock.now += it }, io, { TerminalCapabilities(true, "xterm", 80, 33, false) },
            activeScreen(io), visualizationTitle = { _, _ -> "Picture" }).run(request.copy(visualizationEnabled = true))

        val panel = assertNotNull(io.writes.firstOrNull { "Picture" in it })
        val beforePicture = panel.substringBefore("Picture")
        for (core in 0 until 10) assertContains(beforePicture, "System CPU $core ")
        assertContains(panel.substringAfter("Picture"), "*")
    }

    @Test fun sidePicturePreservesGpuAndEveryCore() {
        val clock = Clock()
        val io = Output()
        val descriptor = RunDescriptor(ComparisonKey("test", "1", "", "", "work"),
            request.parameters, emptyMap(), WorkDefinition("work", "unit", 1.0, "unit/s"),
            2, 2, "unit", emptyList())
        val frame = VisualizationFrame(1, BooleanArray(80 * 60) { it == 0 })
        val handle = object : RunHandle {
            override fun latest() = snapshot.copy(descriptor = descriptor, backend = BackendInfo(null, "GPU", 7u),
                gpu = GpuCommandStatus(GpuCommandState.SUBMITTED, null))
            override fun outcome(): RunOutcome? = if (clock.now >= 400_000_000L) RunOutcome.Failure("done", false) else null
            override fun awaitCompletion() = outcome()!!
            override fun latestVisualization() = frame
        }
        LiveRunDriver(RunWorker { handle }, clock, ResourceSampler {
            ResourceSnapshot(clock.now, (0 until 10).map { CoreLoad(it, 10.0) }, 10.0, null,
                listOf(GpuLoad(7u, 16, 94.0)))
        }, WaitStrategy { clock.now += it }, io, { TerminalCapabilities(true, "xterm", 120, 32, false) },
            activeScreen(io), visualizationTitle = { _, _ -> "Picture" }).run(request.copy(mode = ExecutionMode.METAL, visualizationEnabled = true))
        val picture = io.writes.first { it.contains("Picture") }
        assertTrue(picture.lineSequence().first().contains("Algorithm: test"), picture)
        for (core in 0 until 10) assertTrue(picture.contains("System CPU $core "), picture)
        assertTrue(picture.contains("System GPU  94%"), picture)
        assertTrue(picture.contains("GPU history:"), picture)
        assertTrue(picture.contains("GPU command duration:"), picture)
    }

    @Test fun visibilityFailureDoesNotStopCalculationOrCleanup() {
        val clock = Clock()
        val io = Output()
        val descriptor = RunDescriptor(ComparisonKey("test", "1", "", "", "work"),
            request.parameters, emptyMap(), WorkDefinition("work", "unit", 1.0, "unit/s"),
            2, 2, "unit", emptyList())
        val expected = RunOutcome.Success(RunResult(request, descriptor, BackendInfo(1, null),
            CheckedSummary(emptyList()), 1, 2, 3))
        var requests = 0
        val handle = object : RunHandle {
            override fun latest() = snapshot.copy(descriptor = descriptor)
            override fun outcome(): RunOutcome? = if (clock.now >= 400_000_000L) expected else null
            override fun awaitCompletion() = expected
            override fun setVisualizationVisible(visible: Boolean) { error("Visibility failed") }
            override fun requestVisualization() { requests++ }
        }
        val result = LiveRunDriver(RunWorker { handle }, clock,
            ResourceSampler { ResourceSnapshot(clock.now, listOf(CoreLoad(0, 10.0)), 10.0, null) },
            WaitStrategy { clock.now += it }, io, { TerminalCapabilities(true, "xterm", 120, 32, false) },
            activeScreen(io), visualizationTitle = { _, _ -> "Picture" }).run(request.copy(visualizationEnabled = true))
        assertEquals(expected, result)
        assertEquals(0, requests)
        assertTrue(io.text.contains("System CPU 0"))
    }

    @Test fun drawingFailureStopsRequestsAndKeepsMeasurements() {
        val clock = Clock()
        val io = Output()
        val descriptor = RunDescriptor(ComparisonKey("test", "1", "", "", "work"),
            request.parameters, emptyMap(), WorkDefinition("work", "unit", 1.0, "unit/s"),
            2, 2, "unit", emptyList())
        var requests = 0
        var hidden = false
        val handle = object : RunHandle {
            override fun latest() = snapshot.copy(descriptor = descriptor)
            override fun outcome(): RunOutcome? = if (clock.now >= 400_000_000L) RunOutcome.Failure("done", false) else null
            override fun awaitCompletion() = outcome()!!
            override fun setVisualizationVisible(visible: Boolean) { if (!visible) hidden = true }
            override fun requestVisualization() { requests++ }
            override fun latestVisualization(): VisualizationFrame? = error("Drawing failed")
        }
        val result = LiveRunDriver(RunWorker { handle }, clock,
            ResourceSampler { ResourceSnapshot(clock.now, listOf(CoreLoad(0, 10.0)), 10.0, null) },
            WaitStrategy { clock.now += it }, io, { TerminalCapabilities(true, "xterm", 120, 32, false) },
            activeScreen(io), visualizationTitle = { _, _ -> "Picture" }).run(request.copy(visualizationEnabled = true))
        assertIs<RunOutcome.Failure>(result)
        assertEquals(1, requests)
        assertTrue(hidden)
        assertTrue(io.text.contains("System CPU 0"))
    }

    @Test fun samplingAndPlainOutputRespectTheirSchedules() {
        val (io, samples) = exercise({ TerminalCapabilities(false, null, 80, 24, false) }) {
            ResourceSnapshot(it, listOf(CoreLoad(0, 50.0)), 10.0, null)
        }
        assertTrue(samples.size >= 4)
        assertTrue(samples.zipWithNext().all { (a, b) -> b - a >= 500_000_000L })
        assertEquals(3, Regex("System CPU 0").findAll(io.text).count())
        assertFalse(io.text.contains('\u001b'))
    }
    @Test fun dumbAndResizedTerminalsUsePlainOutputWithAllCores() {
        val (dumb, _) = exercise({ TerminalCapabilities(true, "dumb", 80, 24, false) }) {
            ResourceSnapshot(it, listOf(CoreLoad(0, 10.0), CoreLoad(1, 20.0)), 10.0, null)
        }
        assertFalse(dumb.text.contains('\u001b'))
        val (resized, _) = exercise({ time -> TerminalCapabilities(true, "xterm", if (time < 700_000_000L) 100 else 10, 24, false) }, true) {
            ResourceSnapshot(it, listOf(CoreLoad(0, 10.0), CoreLoad(1, 20.0)), 10.0, null)
        }
        assertTrue(resized.text.contains("System CPU 0"))
        assertTrue(resized.text.contains("System CPU 1"))
        assertTrue(resized.text.contains("\u001b["))
    }
    @Test fun plainScreenNeverUsesCursorCodesOnKnownTty() {
        val (io, _) = exercise({ TerminalCapabilities(true, "xterm", 120, 30, false) }) {
            ResourceSnapshot(it, listOf(CoreLoad(0, 50.0)), 10.0, null)
        }
        assertFalse(io.text.contains('\u001b'))
    }

    @Test fun unknownTerminalDoesNotFitPanel() {
        val caps = TerminalCapabilities(true, "unverified-terminal", 120, 30, false)
        assertFalse(PanelRenderer().fits(listOf("Progress"), caps))
    }

    @Test fun staleAndFailedSamplesDoNotStopTheRun() {
        val (io, _) = exercise({ TerminalCapabilities(false, null, 80, 24, false) }) {
            if (it == 0L) ResourceSnapshot(0, listOf(CoreLoad(0, 50.0)), 10.0, null)
            else throw IllegalStateException("sample failed")
        }
        assertTrue(io.text.contains("N/A"))
        assertTrue(io.text.contains("sample failed"))
    }
    @Test fun elapsedAdvancesWhileTheFirstUnitRemainsIncomplete() {
        val (io, samples) = exercise({ TerminalCapabilities(false, null, 100, 30, false) }) {
            ResourceSnapshot(it, listOf(CoreLoad(0, 50.0)), 10.0, null)
        }
        assertTrue(samples.any { it >= 1_000_000_000L })
        assertTrue(io.text.contains("Elapsed: 1"))
        assertTrue(io.text.contains("0/2"))
        assertTrue(io.text.contains("ETA: N/A"))
    }
    @Test fun staleSampleExpiresAfterOneAndHalfSeconds() {
        val (io, _) = exercise({ TerminalCapabilities(false, null, 80, 24, false) }) {
            ResourceSnapshot(0, listOf(CoreLoad(0, 50.0)), 10.0, null)
        }
        assertTrue(io.text.contains("50"))
        assertTrue(io.writes.last().contains("N/A"))
    }
    @Test fun ttyRendersAtMostFiveTimesPerSecond() {
        val clock = Clock()
        val times = mutableListOf<Long>()
        val io = object : TerminalIo {
            override fun readLine(): String? = null
            override fun write(text: String) { if (text.contains("Progress:")) times.add(clock.now) }
        }
        val handle = object : RunHandle {
            override fun latest() = snapshot
            override fun outcome(): RunOutcome? = if (clock.now >= 1_100_000_000L) RunOutcome.Failure("done", false) else null
            override fun awaitCompletion() = outcome()!!
        }
        LiveRunDriver(RunWorker { handle }, clock, ResourceSampler { ResourceSnapshot(clock.now, listOf(CoreLoad(0, 50.0)), 0.0, null) },
            WaitStrategy { clock.now += it }, io, { TerminalCapabilities(true, "xterm", 120, 30, false) }, activeScreen(io)).run(request)
        assertTrue(times.size >= 5)
        assertTrue(times.zipWithNext().all { (a, b) -> b - a >= 200_000_000L })
    }

    @Test fun keepsTheLivePanelVisibleBetweenProgressUpdates() {
        val (io, _) = exercise({ TerminalCapabilities(true, "xterm", 100, 30, true) }, active = true) {
            ResourceSnapshot(it, listOf(CoreLoad(0, 50.0)), 10.0, null)
        }
        val first = io.writes.indexOfFirst { "Progress:" in it }
        val last = io.writes.indexOfLast { "Progress:" in it }
        assertTrue(first >= 0 && last > first)
        val liveOutput = io.writes.subList(first, last + 1)
        assertFalse(liveOutput.any { "\u001b[2J" in it }, "The screen went blank between progress updates.")
    }

    @Test fun ttyPanelLinesDoNotReachLastColumn() {
        val renderer = PanelRenderer()
        val caps = TerminalCapabilities(true, "xterm", 80, 24, false)
        val lines = renderer.lines(snapshot, ResourceSnapshot(0, listOf(CoreLoad(0, 50.0)), 10.0, null), 0, true)
        assertTrue(renderer.fits(lines, caps))
        assertTrue(lines.all { it.length < caps.columns })
        assertFalse(renderer.fits(lines, caps.copy(columns = lines.maxOf { it.length })))
    }
    @Test fun closesOwnedSamplerAfterRunFailure() {
        val clock = Clock()
        var closed = false
        val sampler = object : ResourceSampler, AutoCloseable {
            override fun sample() = ResourceSnapshot(clock.now, emptyList(), null, null)
            override fun close() { closed = true }
        }
        val handle = object : RunHandle {
            override fun latest() = snapshot
            override fun outcome(): RunOutcome? = RunOutcome.Failure("failed", false)
            override fun awaitCompletion() = outcome()!!
        }
        LiveRunDriver(RunWorker { handle }, clock, sampler, WaitStrategy {}, Output(),
            { TerminalCapabilities(false, null, 80, 24, false) }).run(request)
        assertTrue(closed)
    }

    @Test fun closesOwnedSamplerWhenWorkerCannotStart() {
        var closed = false
        val sampler = object : ResourceSampler, AutoCloseable {
            override fun sample() = ResourceSnapshot(0, emptyList(), null, null)
            override fun close() { closed = true }
        }
        val driver = LiveRunDriver(RunWorker { throw IllegalStateException("start failed") }, Clock(), sampler,
            WaitStrategy {}, Output(), { TerminalCapabilities(false, null, 80, 24, false) })
        assertFailsWith<IllegalStateException> { driver.run(request) }
        assertTrue(closed)
    }

    @Test fun waitsForWorkerWhenOutputFails() {
        var waited = false
        val handle = object : RunHandle {
            override fun latest() = snapshot
            override fun outcome(): RunOutcome? = null
            override fun awaitCompletion(): RunOutcome { waited = true; return RunOutcome.Failure("done", false) }
        }
        val io = object : TerminalIo {
            override fun readLine(): String? = null
            override fun write(text: String) { throw IllegalStateException("output failed") }
        }
        val driver = LiveRunDriver(RunWorker { handle }, Clock(), ResourceSampler { ResourceSnapshot(0, emptyList(), null, null) },
            WaitStrategy {}, io, { TerminalCapabilities(false, null, 80, 24, false) })
        assertFailsWith<IllegalStateException> { driver.run(request) }
        assertTrue(waited)
    }

    @Test fun resizeErasesFixedPanelBeforePlainSnapshotWithEveryCore() {
        for (short in listOf(false, true)) {
            val (io, _) = exercise({ time ->
                if (time < 700_000_000L) TerminalCapabilities(true, "xterm", 100, 30, false)
                else if (short) TerminalCapabilities(true, "xterm", 100, 3, false)
                else TerminalCapabilities(true, "xterm", 10, 30, false)
            }, true) { ResourceSnapshot(it, listOf(CoreLoad(0, 10.0), CoreLoad(1, 20.0)), 10.0, null) }
            val lastErase = io.writes.indexOfLast { it.contains('\u001b') }
            assertTrue(lastErase > 0)
            assertEquals("\u001b[2J\u001b[H", io.writes[lastErase])
            assertFalse(io.writes[lastErase].contains('\n'))
            val fallback = io.writes.drop(lastErase + 1).first { it.contains("Progress:") }
            assertFalse(fallback.contains('\u001b'))
            assertTrue(fallback.contains("System CPU 0  [#.........]  10%"))
            assertTrue(fallback.contains("System CPU 1  [##........]  20%"))
            assertTrue(io.writes.drop(lastErase + 1).none { it.contains('\u001b') })
        }
    }

    @Test fun completionReturnsBeforeNextPlainSnapshot() {
        val clock = Clock()
        val io = Output()
        val expected = RunOutcome.Failure("done", false)
        val handle = object : RunHandle {
            override fun latest() = snapshot
            override fun outcome(): RunOutcome? = if (clock.now >= 500_000_000L) expected else null
            override fun awaitCompletion() = outcome()!!
        }
        val result = LiveRunDriver(RunWorker { handle }, clock,
            ResourceSampler { ResourceSnapshot(clock.now, listOf(CoreLoad(0, 50.0)), 10.0, null) },
            WaitStrategy { clock.now += it }, io, { TerminalCapabilities(false, null, 80, 24, false) }).run(request)
        assertEquals(expected, result)
        assertEquals(500_000_000L, clock.now)
        assertEquals(1, io.writes.count { it.contains("Progress:") })
    }

    @Test fun drainsPhasesPublishedBetweenInitialDrainAndOutcome() {
        val io = Output()
        val preparing = snapshot.copy(phase = RunPhase.PREPARING, backend = null)
        val warming = snapshot.copy(phase = RunPhase.WARMING_UP)
        var finished = false
        val handle = object : RunHandle {
            override fun latest() = warming
            override fun drainTransitions(): List<RunSnapshot> = if (finished) listOf(preparing, warming) else emptyList()
            override fun outcome(): RunOutcome { finished = true; return RunOutcome.Failure("done", false) }
            override fun awaitCompletion() = outcome()
        }
        LiveRunDriver(RunWorker { handle }, Clock(), ResourceSampler { ResourceSnapshot(0, emptyList(), null, null) },
            WaitStrategy {}, io, { TerminalCapabilities(false, null, 80, 24, false) }).run(request)
        assertTrue(io.text.contains("Preparing."))
        assertTrue(io.text.contains("CPU workers: 1"))
        assertTrue(io.text.contains("Warming up."))
    }

    @Test fun phaseNotificationErasesPanelBeforeBackendOutput() {
        val clock = Clock()
        val io = Output()
        var phaseReady = false
        var acknowledged = false
        val warming = snapshot.copy(phase = RunPhase.WARMING_UP)
        val handle = object : RunHandle {
            override fun latest() = snapshot
            override fun drainTransitions(): List<RunSnapshot> = if (phaseReady && !acknowledged) listOf(warming) else emptyList()
            override fun acknowledgeContext() { acknowledged = true }
            override fun outcome(): RunOutcome? = if (acknowledged) RunOutcome.Failure("done", false) else null
            override fun awaitCompletion() = outcome()!!
            override fun waitForChange(version: Long, durationNs: Long, fallback: WaitStrategy) {
                clock.now = 100_000_000L
                phaseReady = true
            }
        }
        val result = LiveRunDriver(RunWorker { handle }, clock,
            ResourceSampler { ResourceSnapshot(clock.now, listOf(CoreLoad(0, 50.0)), 10.0, null) },
            WaitStrategy { fail("The worker must notify the terminal.") }, io,
            { TerminalCapabilities(true, "xterm", 100, 30, false) }, activeScreen(io)).run(request)
        assertIs<RunOutcome.Failure>(result)
        val panel = io.writes.indexOfFirst { it.contains("Progress:") }
        val context = io.writes.indexOfFirst { it.contains("CPU workers: 1") }
        val erase = io.writes.indexOfFirst { it.contains("\u001b[2J") }
        assertTrue(panel >= 0 && panel < erase && erase < context)
        assertTrue(acknowledged)
        assertEquals(100_000_000L, clock.now)
    }


    @Test fun fullScreenRedrawKeepsRunContext() {
        val clock = Clock()
        val io = Output()
        val descriptor = RunDescriptor(ComparisonKey("test", "1", "items=4", "FP32", "work"),
            request.parameters, emptyMap(), WorkDefinition("work", "samples", 1.0, "samples/s"),
            8, 2, "batches", emptyList())
        val current = snapshot.copy(descriptor = descriptor)
        var finalFrame = ""
        val handle = object : RunHandle {
            override fun latest() = current
            override fun outcome(): RunOutcome? {
                if (clock.now < 500_000_000L) return null
                if (finalFrame.isEmpty()) finalFrame = io.text.substringAfterLast("\u001b[2J\u001b[H")
                return RunOutcome.Failure("done", false)
            }
            override fun awaitCompletion() = outcome()!!
        }
        LiveRunDriver(RunWorker { handle }, clock,
            ResourceSampler { ResourceSnapshot(clock.now, emptyList(), null, null) },
            WaitStrategy { clock.now += it }, io, { TerminalCapabilities(true, "xterm", 120, 30, false) },
            activeScreen(io)).run(request)
        for (text in listOf("Algorithm: test", "Mode: CPU_SINGLE", "Parameters: items=4", "Settings: FP32",
            "Work: 8 samples", "CPU workers: 1", "Progress:")) assertTrue(finalFrame.contains(text), finalFrame)
    }

    @Test fun gpuSampleRemainsAvailableWhenSamplingAdvancesClock() {
        val clock = Clock()
        val io = Output()
        val gpuSnapshot = snapshot.copy(backend = BackendInfo(null, "GPU", 7u),
            gpu = GpuCommandStatus(GpuCommandState.SUBMITTED, null))
        val handle = object : RunHandle {
            override fun latest() = gpuSnapshot
            override fun outcome(): RunOutcome? = if (clock.now >= 500_000_000L) RunOutcome.Failure("done", false) else null
            override fun awaitCompletion() = outcome()!!
        }
        LiveRunDriver(RunWorker { handle }, clock, ResourceSampler {
            clock.now += 1
            ResourceSnapshot(clock.now, emptyList(), null, null, listOf(GpuLoad(7u, 16, 94.0)))
        }, WaitStrategy { clock.now += it }, io, { TerminalCapabilities(false, null, 80, 24, false) })
            .run(request.copy(mode = ExecutionMode.METAL))
        val firstPanel = io.writes.first { it.contains("System GPU") }
        assertTrue(firstPanel.contains("System GPU  94%"), firstPanel)
    }

    @Test fun fastGpuCompletionPrintsFinalCommandStatus() {
        val durationCases = listOf(42_000_000L to "42 ms", null to "N/A")
        for ((duration, expectedDuration) in durationCases) {
            val io = Output()
            val gpuRequest = request.copy(mode = ExecutionMode.METAL)
            val descriptor = RunDescriptor(ComparisonKey("test", "1", "", "", "work"),
                gpuRequest.parameters, emptyMap(), WorkDefinition("work", "unit", 1.0, "unit/s"),
                1, 1, "unit", emptyList())
            val result = RunOutcome.Success(RunResult(gpuRequest, descriptor, BackendInfo(null, "Test GPU"),
                CheckedSummary(emptyList()), 1, 2, 3))
            val final = snapshot.copy(phase = RunPhase.SUCCEEDED, backend = result.result.backend,
                gpu = GpuCommandStatus(GpuCommandState.COMPLETED, duration))
            val handle = object : RunHandle {
                override fun latest() = final
                override fun outcome() = result
                override fun awaitCompletion() = result
            }
            val actual = LiveRunDriver(RunWorker { handle }, Clock(),
                ResourceSampler { ResourceSnapshot(0, emptyList(), null, null) }, WaitStrategy {}, io,
                { TerminalCapabilities(false, null, 80, 24, false) }).run(gpuRequest)
            assertEquals(result.result.copy(gpu = final.gpu), (actual as RunOutcome.Success).result)
            assertEquals(1, Regex("GPU: Test GPU  COMPLETED").findAll(io.text).count())
            assertTrue(io.text.contains("GPU command duration: $expectedDuration"))
        }
    }

}
