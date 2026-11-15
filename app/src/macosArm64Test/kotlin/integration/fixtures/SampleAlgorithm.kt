package integration.fixtures

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.*

class SampleAlgorithm(private val onExecute: () -> Unit = {}) : DemoAlgorithm {
    override val info = AlgorithmInfo("sample", "test-1", "Sample", "sample work")
    override val parameters = listOf(
        ParameterDefinition.Integer("items", "items", 3, 1, 10, "Enter items."),
        ParameterDefinition.Decimal("gain", "gain", 1.0, 0.0, 2.0, "Enter gain."),
    )
    override val supportedModes = setOf(ExecutionMode.CPU_SINGLE)
    override fun availability(mode: ExecutionMode) = ModeAvailability(mode, mode == ExecutionMode.CPU_SINGLE, if (mode == ExecutionMode.CPU_SINGLE) null else "Mode is not supported.")
    override fun validate(values: ParameterValues) = ParameterValidation.Valid(values)
    override fun describe(values: ParameterValues): RunDescriptor {
        val items = (values["items"] as ParameterValue.Integer).value
        val gain = (values["gain"] as ParameterValue.Decimal).value
        val work = WorkDefinition("sample-work", "samples", 1.0, "samples/s")
        return RunDescriptor(ComparisonKey(info.id, info.modelVersion, "items=$items,gain=$gain", "FP32", work.id),
            values, mapOf("precision" to "FP32"), work, items, 1, "batches", listOf(ResultFieldDefinition("value", "value", "samples")))
    }
    override fun prepare(request: RunRequest, gpu: GpuStatusSink): PreparedRun = object : PreparedRun {
        override val backend = BackendInfo(1, null)
        override fun warmUp() = Unit
        override fun execute(progress: ProgressSink, gpu: GpuStatusSink) {
            onExecute()
            progress.publish(RunProgress(1, 1, (request.parameters["items"] as ParameterValue.Integer).value))
        }
        override fun checkAndSummarize() = CheckedSummary(listOf(ResultFieldValue("value", "sample result")))
        override fun close() = Unit
    }
}
