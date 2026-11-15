package dev.macmetaldemo.application

import dev.macmetaldemo.domain.*
import kotlin.test.*

class RunHistoryTest {
    private val values = ParameterValues(emptyMap())
    private val algorithm = object : DemoAlgorithm {
        override val info = AlgorithmInfo("a", "1", "A", "Test")
        override val parameters = emptyList<ParameterDefinition>()
        override val supportedModes = setOf(ExecutionMode.CPU_SINGLE, ExecutionMode.METAL)
        override fun availability(mode: ExecutionMode) = ModeAvailability(mode, true, null)
        override fun validate(values: ParameterValues): ParameterValidation = ParameterValidation.Valid(values)
        override fun describe(values: ParameterValues): RunDescriptor = error("unused")
        override fun prepare(request: RunRequest, gpu: GpuStatusSink): PreparedRun = error("unused")
    }
    private fun history() = RunHistory(AlgorithmRegistry(listOf(algorithm)))
    private fun result(mode: ExecutionMode, time: Long, key: String = "a", count: Long? = 12): RunResult {
        val descriptor = RunDescriptor(ComparisonKey(key, "1", "p", "fp32", "w"), values, emptyMap(),
            WorkDefinition("w", "samples", 1.0, "samples/s"), count, 2, "batches", emptyList())
        return RunResult(RunRequest(key, mode, values), descriptor, BackendInfo(1, null), CheckedSummary(emptyList()), 0, time, time)
    }
    @Test fun comparesOnlyMatchingBaselines() {
        val history = history(); val gpu = result(ExecutionMode.METAL, 20)
        assertNull(history.speedIncrease(gpu))
        history.record(result(ExecutionMode.CPU_SINGLE, 80, "other"))
        assertNull(history.speedIncrease(gpu))
        history.record(result(ExecutionMode.CPU_SINGLE, 80))
        assertEquals(4.0, history.speedIncrease(gpu))
    }
    @Test fun zeroTimeAndUnknownWorkHaveNoRateOrSpeed() {
        val history = history(); history.record(result(ExecutionMode.CPU_SINGLE, 80))
        val zero = result(ExecutionMode.METAL, 0)
        assertNull(history.speedIncrease(zero)); assertNull(zero.workRate())
        assertNull(result(ExecutionMode.METAL, 20, count = null).workRate())
        assertEquals(600000000.0, result(ExecutionMode.METAL, 20).workRate())
    }
    @Test fun eachComparisonKeyDimensionSeparatesHistory() {
        val baseline = result(ExecutionMode.CPU_SINGLE, 80)
        val key = baseline.descriptor.key
        val history = history(); history.record(baseline)
        val variants = listOf(
            key.copy(algorithmId = "b"), key.copy(modelVersion = "2"),
            key.copy(canonicalParameters = "other"), key.copy(numericalSettings = "fp64"),
            key.copy(workDefinitionId = "other"),
        )
        for (variant in variants) {
            val descriptor = RunDescriptor(variant, values, emptyMap(), baseline.descriptor.work, 12, 2, "batches", emptyList())
            val selected = baseline.copy(request = baseline.request.copy(mode = ExecutionMode.METAL), descriptor = descriptor, computeNs = 20)
            assertNull(history.speedIncrease(selected), variant.toString())
        }
    }

    @Test fun visualizationChoiceKeepsSeparateBaselines() {
        val history = history()
        val off = result(ExecutionMode.CPU_SINGLE, 80)
        val on = off.copy(request = off.request.copy(visualizationEnabled = true), computeNs = 100)
        history.record(off)
        val selected = result(ExecutionMode.METAL, 20)
        val visible = selected.copy(request = selected.request.copy(visualizationEnabled = true))
        assertEquals(4.0, history.speedIncrease(selected))
        assertNull(history.speedIncrease(visible))
        history.record(on)
        assertEquals(5.0, history.speedIncrease(visible))
        assertEquals(4.0, history.speedIncrease(selected))
    }

}
