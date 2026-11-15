package dev.macmetaldemo.terminal

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.*
import kotlin.test.*

private class Io(lines: List<String>) : TerminalIo {
    private val input = lines.iterator()
    var output = ""
    override fun readLine(): String? = if (input.hasNext()) input.next() else null
    override fun write(text: String) { output += text }
}
private open class Example(val id: String = "example") : DemoAlgorithm {
    override val info = AlgorithmInfo(id, "1", id, "description")
    override val parameters = listOf(ParameterDefinition.Integer("items", "items", 4, 1, 10, "Bad items."), ParameterDefinition.Decimal("gain", "gain", 1.0, 0.0, 2.0, "Bad gain."))
    override open val supportedModes = setOf(ExecutionMode.CPU_SINGLE, ExecutionMode.CPU_PARALLEL)
    override open fun availability(mode: ExecutionMode) = ModeAvailability(mode, mode == ExecutionMode.CPU_SINGLE, if (mode == ExecutionMode.CPU_SINGLE) null else "Unavailable.")
    override open fun validate(values: ParameterValues): ParameterValidation = ParameterValidation.Valid(values)
    override open fun describe(values: ParameterValues) = RunDescriptor(ComparisonKey(id, "1", "items=4,gain=1.0", "FP32", "sample"), values, mapOf("precision" to "FP32"), WorkDefinition("sample", "samples", 1.0, "samples/s"), 8, 2, "batches", listOf(ResultFieldDefinition("sum", "sum", null)))
    override open fun prepare(request: RunRequest, gpu: GpuStatusSink): PreparedRun = error("Not used")
}
private fun success(request: RunRequest, algorithm: Example): RunOutcome.Success = RunOutcome.Success(RunResult(request, algorithm.describe(request.parameters), BackendInfo(1, null), CheckedSummary(listOf(ResultFieldValue("sum", "7"))), 2, 4, 8))

class TerminalSessionTest {
    @Test fun failedVisualizationTitleDoesNotStopRun() {
        val algorithm = object : Example() {
            override val visualizationTitle: String? get() = error("Title failed.")
        }
        val registry = AlgorithmRegistry(listOf(algorithm))
        val io = Io(listOf("1", "1", "", "", "", "0"))
        val screen = object : TerminalScreen {
            override val active = true
            override fun clear() = Unit
            override fun close() = Unit
        }
        var completed = false
        val status = TerminalSession(registry, RunDriver { request ->
            assertFalse(request.visualizationEnabled)
            completed = true
            success(request, algorithm)
        }, RunHistory(registry), io, screen).run()
        assertEquals(0, status)
        assertTrue(completed)
        assertFalse(io.output.contains("Show visualization?"))
    }

    @Test fun registeredSortingAlgorithmDrawsThroughTheSameSession() {
        var titleReads = 0
        val algorithm = object : Example("sorting") {
            override val visualizationTitle: String get() {
                titleReads++
                if (titleReads > 1) error("Title read again.")
                return "Sorting bars"
            }
            override fun prepare(request: RunRequest, gpu: GpuStatusSink) = object : PreparedRun {
                override val backend = BackendInfo(1, null)
                private var port: VisualizationPort = NoVisualization
                private val values = intArrayOf(3, 1, 2)
                override fun attachVisualization(port: VisualizationPort) { this.port = port }
                override fun warmUp() = Unit
                override fun execute(progress: ProgressSink, gpu: GpuStatusSink) {
                    values.sort()
                    if (port.captureRequested()) {
                        val dots = BooleanArray(80 * 60)
                        values.forEachIndexed { column, value ->
                            repeat(value) { row -> dots[(59 - row) * 80 + column * 2] = true }
                        }
                        port.publish(VisualizationFrame(1, dots))
                    }
                    progress.publish(RunProgress(2, 2, 8))
                }
                override fun checkAndSummarize() = CheckedSummary(listOf(ResultFieldValue("sum", values.joinToString(","))))
                override fun close() = Unit
            }
        }
        val registry = AlgorithmRegistry(listOf(algorithm))
        val io = Io(listOf("1", "1", "", "", "", "", "0"))
        val screen = object : TerminalScreen {
            override val active = true
            override fun clear() = Unit
            override fun close() = Unit
        }
        var time = 0L
        val clock = MonotonicClock { time }
        val controller = RunController(registry, clock)
        val worker = RunWorker { request ->
            var capturedFrame: VisualizationFrame? = null
            var outcome: RunOutcome? = null
            val port = object : VisualizationPort {
                override fun captureRequested() = true
                override fun publish(frame: VisualizationFrame) { capturedFrame = frame }
                override fun disable() = Unit
            }
            object : RunHandle {
                override fun latest() = RunSnapshot(RunPhase.RUNNING, algorithm.describe(request.parameters),
                    BackendInfo(1, null), RunProgress(0, 2, 0), 0, 0, null, null)
                override fun requestVisualization() { if (outcome == null) outcome = controller.run(request, port) {} }
                override fun latestVisualization() = capturedFrame
                override fun outcome() = outcome
                override fun awaitCompletion() = outcome!!
            }
        }
        val driver = LiveRunDriver(worker, clock, ResourceSampler { ResourceSnapshot(time, emptyList(), null, null) },
            WaitStrategy { time += it }, io, { TerminalCapabilities(true, "xterm", 120, 32, false) }, screen,
            { registry.find(it)?.info?.name ?: it }, { registry.find(it)?.visualizationTitle })
        assertEquals(0, TerminalSession(registry, driver, RunHistory(registry), io, screen).run())
        assertTrue(io.output.contains("1  sorting - description"))
        assertTrue(io.output.contains("Sorting bars"))
        assertTrue(io.output.contains("*"))
        assertTrue(io.output.contains("1,2,3"))
    }

    @Test fun visualizationChoiceRemainsSeparateForEachAlgorithm() {
        val first = object : Example("first") { override val visualizationTitle = "First picture" }
        val second = object : Example("second") { override val visualizationTitle = "Second picture" }
        val registry = AlgorithmRegistry(listOf(first, second))
        val io = Io(listOf("1", "1", "", "", "bad", "n", "", "2", "1", "", "", "", "", "1", "1", "", "", "", "", "0"))
        val screen = object : TerminalScreen {
            override val active = true
            override fun clear() = Unit
            override fun close() = Unit
        }
        val requests = mutableListOf<RunRequest>()
        TerminalSession(registry, RunDriver { request ->
            requests.add(request)
            success(request, if (request.algorithmId == "first") first else second)
        }, RunHistory(registry), io, screen).run()
        assertEquals(listOf(false, true, false), requests.map { it.visualizationEnabled })
        assertTrue(io.output.contains("Show visualization? [y/N]:"))
        assertTrue(io.output.contains("Select yes or no."))
    }

    @Test fun eofAtVisualizationPromptDoesNotStartRun() {
        val algorithm = object : Example() { override val visualizationTitle = "Picture" }
        val registry = AlgorithmRegistry(listOf(algorithm))
        val io = Io(listOf("1", "1", "", ""))
        val screen = object : TerminalScreen {
            override val active = true
            override fun clear() = Unit
            override fun close() = Unit
        }
        assertEquals(0, TerminalSession(registry, RunDriver { fail("Unexpected run") }, RunHistory(registry), io, screen).run())
    }

    @Test fun plainOutputSkipsVisualizationPrompt() {
        val algorithm = object : Example() { override val visualizationTitle = "Picture" }
        val registry = AlgorithmRegistry(listOf(algorithm))
        val io = Io(listOf("1", "1", "", "", "0"))
        var selected = true
        TerminalSession(registry, RunDriver { selected = it.visualizationEnabled; success(it, algorithm) }, RunHistory(registry), io).run()
        assertFalse(selected)
        assertFalse(io.output.contains("Show visualization?"))
    }

    @Test fun fullScreenResultKeepsFinalGpuStatus() {
        for ((duration, expected) in listOf(42_000_000L to "42 ms", null to "N/A")) {
            val algorithm = object : Example() {
                override val supportedModes = setOf(ExecutionMode.METAL)
                override fun availability(mode: ExecutionMode) = ModeAvailability(mode, mode == ExecutionMode.METAL, null)
            }
            val registry = AlgorithmRegistry(listOf(algorithm))
            val io = Io(listOf("1", "3", "", ""))
            val screen = object : TerminalScreen {
                override val active = true
                override fun clear() = io.write("\u001b[2J\u001b[H")
                override fun close() = Unit
            }
            val driver = RunDriver { request ->
                val result = success(request, algorithm)
                val final = RunSnapshot(RunPhase.SUCCEEDED, result.result.descriptor, BackendInfo(null, "Test GPU"),
                    null, null, null, null, GpuCommandStatus(GpuCommandState.COMPLETED, duration))
                val handle = object : RunHandle {
                    override fun latest() = final
                    override fun outcome() = result
                    override fun awaitCompletion() = result
                }
                LiveRunDriver(RunWorker { handle }, object : MonotonicClock { override fun nowNs() = 0L },
                    ResourceSampler { ResourceSnapshot(0, emptyList(), null, null) }, WaitStrategy {}, io,
                    { TerminalCapabilities(true, "xterm", 120, 30, false) }, screen).run(request)
            }
            assertEquals(0, TerminalSession(registry, driver, RunHistory(registry), io, screen).run())
            val resultView = io.output.substringAfterLast("\u001b[2J\u001b[H")
            assertTrue(resultView.contains("COMPLETED"), resultView)
            assertTrue(resultView.contains("GPU command duration: $expected"), resultView)
        }
    }

    @Test fun fullScreenWaitsForResultAcknowledgment() {
        val algorithm = Example()
        val registry = AlgorithmRegistry(listOf(algorithm))
        val io = Io(listOf("1", "1", "", "", "", "0"))
        var clears = 0
        val screen = object : TerminalScreen {
            override val active = true
            override fun clear() { clears++ }
            override fun close() = Unit
        }
        val result = TerminalSession(registry, RunDriver { success(it, algorithm) }, RunHistory(registry), io, screen).run()
        assertEquals(0, result)
        assertTrue(io.output.contains("Press Enter to continue."))
        assertEquals(2, Regex("Select an algorithm:").findAll(io.output).count())
        assertTrue(clears >= 4)
    }

    @Test fun fullScreenWaitsAfterRunFailure() {
        val registry = AlgorithmRegistry(listOf(Example()))
        val io = Io(listOf("1", "1", "", "", "", "0"))
        val screen = object : TerminalScreen {
            override val active = true
            override fun clear() = Unit
            override fun close() = Unit
        }
        val status = TerminalSession(registry, RunDriver { RunOutcome.Failure("Run failed.", false) },
            RunHistory(registry), io, screen).run()
        assertEquals(0, status)
        assertTrue(io.output.contains("Run failed.\nPress Enter to continue."))
    }

    @Test fun eofAtResultExits() {
        val algorithm = Example()
        val registry = AlgorithmRegistry(listOf(algorithm))
        val io = Io(listOf("1", "1", "", ""))
        val screen = object : TerminalScreen {
            override val active = true
            override fun clear() = Unit
            override fun close() = Unit
        }
        assertEquals(0, TerminalSession(registry, RunDriver { success(it, algorithm) }, RunHistory(registry), io, screen).run())
        assertEquals(1, Regex("Select an algorithm:").findAll(io.output).count())
    }

    @Test fun menuModesAndParametersUseRegistry() {
        val algorithm = Example()
        val registry = AlgorithmRegistry(listOf(algorithm))
        val io = Io(listOf("1", "2", "3", "1", "4", "1", "0"))
        var calls = 0
        val code = TerminalSession(registry, RunDriver { request -> calls++; success(request, algorithm) }, RunHistory(registry), io).run()
        assertEquals(0, code)
        assertEquals(1, calls)
        assertTrue(io.output.contains("1  example - description\n0  Exit"))
        assertTrue(io.output.contains("1  CPU - one thread"))
        assertTrue(io.output.contains("2  CPU - all cores - Unavailable."))
        assertTrue(io.output.contains("3  GPU - Metal - Unavailable."))
        assertTrue(io.output.indexOf("items [") < io.output.indexOf("gain ["))
        assertTrue(io.output.contains("batches"))
        assertTrue(io.output.contains("samples"))
    }
    @Test fun eofAndBackExitWithoutRuns() {
        for (lines in listOf(emptyList(), listOf("1"), listOf("1", "1"), listOf("1", "1", "bad"), listOf("1", "1", "4"), listOf("1", "0", "0"))) {
            val registry = AlgorithmRegistry(listOf(Example()))
            val io = Io(lines)
            assertEquals(0, TerminalSession(registry, RunDriver { fail("Unexpected run") }, RunHistory(registry), io).run())
        }
    }
    @Test fun errorsRecoverAndFatalErrorsExit() {
        for (fatal in listOf(false, true)) {
            val registry = AlgorithmRegistry(listOf(Example()))
            val io = Io(listOf("1", "1", "", "", "0"))
            assertEquals(if (fatal) 1 else 0, TerminalSession(registry, RunDriver { RunOutcome.Failure("Run failed.", fatal) }, RunHistory(registry), io).run())
            assertTrue(io.output.contains("Run failed."))
        }
    }
    @Test fun parametersRemainSeparateAcrossAlgorithms() {
        val first = Example("first")
        val second = Example("second")
        val registry = AlgorithmRegistry(listOf(first, second))
        val io = Io(listOf("1", "1", "7", "1.5", "2", "1", "3", "0.5", "1", "1", "", "", "0"))
        val requests = mutableListOf<RunRequest>()
        TerminalSession(registry, RunDriver { request -> requests.add(request); success(request, if (request.algorithmId == "first") first else second) }, RunHistory(registry), io).run()
        assertEquals(ParameterValue.Integer(7), requests[2].parameters["items"])
        assertEquals(ParameterValue.Decimal(1.5), requests[2].parameters["gain"])
        assertEquals(ParameterValue.Integer(3), requests[1].parameters["items"])
    }
    @Test fun partialInputDoesNotReplaceRememberedValues() {
        val algorithm = Example()
        val registry = AlgorithmRegistry(listOf(algorithm))
        val inputs = listOf<String?>("1", "1", "7", "1.5", "1", "1", "3", null,
            "1", "1", "", "", "0").iterator()
        val io = object : TerminalIo {
            override fun readLine(): String? = if (inputs.hasNext()) inputs.next() else null
            override fun write(text: String) = Unit
        }
        val requests = mutableListOf<RunRequest>()
        val session = TerminalSession(registry, RunDriver { request ->
            requests.add(request)
            success(request, algorithm)
        }, RunHistory(registry), io)
        assertEquals(0, session.run())
        assertEquals(1, requests.size)
        assertEquals(0, session.run())
        assertEquals(2, requests.size)
        assertEquals(ParameterValue.Integer(7), requests[1].parameters["items"])
        assertEquals(ParameterValue.Decimal(1.5), requests[1].parameters["gain"])
    }
    @Test fun anotherAlgorithmErrorPreservesRememberedValues() {
        val first = Example("first")
        val second = Example("second")
        val registry = AlgorithmRegistry(listOf(first, second))
        val io = Io(listOf("1", "1", "7", "1.5", "2", "1", "3", "0.5", "1", "1", "", "", "0"))
        val requests = mutableListOf<RunRequest>()
        val status = TerminalSession(registry, RunDriver { request ->
            requests.add(request)
            if (request.algorithmId == "second") RunOutcome.Failure("Sample failed.", false)
            else success(request, first)
        }, RunHistory(registry), io).run()
        assertEquals(0, status)
        assertTrue(io.output.contains("Sample failed."))
        assertEquals(3, requests.size)
        assertEquals(ParameterValue.Integer(7), requests[2].parameters["items"])
        assertEquals(ParameterValue.Decimal(1.5), requests[2].parameters["gain"])
    }
    @Test fun selectedModeAppearsBeforeDriverStarts() {
        val algorithm = Example()
        val registry = AlgorithmRegistry(listOf(algorithm))
        val io = Io(listOf("1", "1", "", "", "0"))
        val code = TerminalSession(registry, RunDriver { request ->
            assertTrue(io.output.contains("Mode: CPU - one thread\n"))
            success(request, algorithm)
        }, RunHistory(registry), io).run()
        assertEquals(0, code)
    }
    @Test fun nonfatalErrorReturnsToMenuAndStartsAnotherRun() {
        val algorithm = Example()
        val registry = AlgorithmRegistry(listOf(algorithm))
        val io = Io(listOf("1", "1", "", "", "1", "1", "", "", "0"))
        var calls = 0
        val code = TerminalSession(registry, RunDriver { request ->
            calls++
            if (calls == 1) RunOutcome.Failure("Run failed.", false) else success(request, algorithm)
        }, RunHistory(registry), io).run()
        assertEquals(0, code)
        assertEquals(2, calls)
        assertTrue(io.output.contains("Run failed."))
        assertTrue(io.output.contains("Completed: 2 batches"))
    }
    @Test fun callbackFailuresRespectSeverityAndReturnToAlgorithmMenu() {
        for (stage in listOf("availability", "validation", "description")) {
            val broken = object : Example("broken") {
                override fun availability(mode: ExecutionMode): ModeAvailability {
                    if (stage == "availability") throw RunFailure("Unavailable callback")
                    return super.availability(mode)
                }
                override fun validate(values: ParameterValues): ParameterValidation {
                    if (stage == "validation") throw RunFailure("Invalid callback")
                    return super.validate(values)
                }
                override fun describe(values: ParameterValues): RunDescriptor {
                    if (stage == "description") throw RunFailure("Fatal description", true)
                    return super.describe(values)
                }
            }
            val good = Example("good"); val registry = AlgorithmRegistry(listOf(broken, good))
            val first = if (stage == "availability") listOf("1") else listOf("1", "1", "", "")
            val io = Io(first + listOf("2", "1", "", "", "0")); var runs = 0
            val code = TerminalSession(registry, RunDriver { request -> runs++; success(request, good) }, RunHistory(registry), io).run()
            assertEquals(if (stage == "description") 1 else 0, code, stage)
            assertEquals(if (stage == "description") 0 else 1, runs, stage)
        }
    }

}
