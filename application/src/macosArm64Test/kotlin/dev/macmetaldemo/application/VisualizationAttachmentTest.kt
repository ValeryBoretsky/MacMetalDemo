package dev.macmetaldemo.application

import dev.macmetaldemo.domain.*
import kotlin.test.*

class VisualizationAttachmentTest {
    @Test fun failedVisualizationTitleDoesNotStopCalculation() {
        var executed = false
        val values = ParameterValues(emptyMap())
        val algorithm = object : DemoAlgorithm {
            override val visualizationTitle: String? get() = error("Title failed.")
            override val info = AlgorithmInfo("sample", "1", "Sample", "Test")
            override val parameters = emptyList<ParameterDefinition>()
            override val supportedModes = setOf(ExecutionMode.CPU_SINGLE)
            override fun availability(mode: ExecutionMode) = ModeAvailability(mode, true, null)
            override fun validate(values: ParameterValues) = ParameterValidation.Valid(values)
            override fun describe(values: ParameterValues) = RunDescriptor(
                ComparisonKey("sample", "1", "", "", "samples"), values, emptyMap(),
                WorkDefinition("samples", "samples", 1.0, "samples/s"), 1, 1, "units", emptyList(),
            )
            override fun prepare(request: RunRequest, gpu: GpuStatusSink) = object : PreparedRun {
                override val backend = BackendInfo(1, null)
                override fun warmUp() = Unit
                override fun execute(progress: ProgressSink, gpu: GpuStatusSink) {
                    executed = true
                    progress.publish(RunProgress(1, 1, 1))
                }
                override fun checkAndSummarize() = CheckedSummary(emptyList())
                override fun close() = Unit
            }
        }
        val controller = RunController(AlgorithmRegistry(listOf(algorithm)), MonotonicClock { 0L })
        assertIs<RunOutcome.Success>(controller.run(RunRequest("sample", ExecutionMode.CPU_SINGLE, values, true)) {})
        assertTrue(executed)
    }

    @Test fun optionalAttachmentRespectsChoiceAndPreservesTiming() {
        for (enabled in listOf(false, true)) for (broken in listOf(false, true)) {
            var attached = false
            var disabled = false
            val port = object : VisualizationPort {
                override fun captureRequested() = true
                override fun publish(frame: VisualizationFrame) = Unit
                override fun disable() {
                    disabled = true
                    if (broken) error("Drawing cleanup failed.")
                }
            }
            var time = 0L
            val values = ParameterValues(emptyMap())
            val algorithm = object : DemoAlgorithm {
                override val visualizationTitle = "Sample"
                override val info = AlgorithmInfo("sample", "1", "Sample", "Test")
                override val parameters = emptyList<ParameterDefinition>()
                override val supportedModes = setOf(ExecutionMode.CPU_SINGLE)
                override fun availability(mode: ExecutionMode) = ModeAvailability(mode, true, null)
                override fun validate(values: ParameterValues) = ParameterValidation.Valid(values)
                override fun describe(values: ParameterValues) = RunDescriptor(
                    ComparisonKey("sample", "1", "", "", "samples"), values, emptyMap(),
                    WorkDefinition("samples", "samples", 1.0, "samples/s"), 1, 1, "units", emptyList(),
                )
                override fun prepare(request: RunRequest, gpu: GpuStatusSink) = object : PreparedRun {
                    override val backend = BackendInfo(1, null)
                    override fun attachVisualization(port: VisualizationPort) {
                        attached = true
                        if (broken) error("Drawing attachment failed.")
                    }
                    override fun warmUp() { time += 10 }
                    override fun execute(progress: ProgressSink, gpu: GpuStatusSink) {
                        time += 20
                        progress.publish(RunProgress(1, 1, 1))
                    }
                    override fun checkAndSummarize() = CheckedSummary(emptyList())
                    override fun close() = Unit
                }
            }
            val controller = RunController(AlgorithmRegistry(listOf(algorithm)), MonotonicClock { time })
            val outcome = controller.run(RunRequest("sample", ExecutionMode.CPU_SINGLE, values, enabled), port) { }
            assertEquals(enabled, attached)
            assertEquals(enabled && broken, disabled)
            val result = assertIs<RunOutcome.Success>(outcome).result
            assertEquals(20L, result.computeNs)
            assertEquals(10L, result.preparationNs)
        }
    }
}
