package dev.macmetaldemo.terminal

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.*
import kotlin.test.*

class ResultRendererTest {
    @Test fun comparesMetalFirstOnlyAfterMatchingParallelRun() {
        val output = StringBuilder()
        val io = object : TerminalIo {
            override fun readLine(): String? = null
            override fun write(text: String) { output.append(text) }
        }
        val history = RunHistory(AlgorithmRegistry(emptyList()))
        val renderer = ResultRenderer(io, history)
        val values = ParameterValues(emptyMap())
        fun workload(drops: Int) = RunDescriptor(ComparisonKey("test", "2", "drops=$drops", "FP32", "work"), values,
            emptyMap(), WorkDefinition("work", "cells", 1.0, "cells/s"), 12, 3, "ticks", emptyList())
        val gpu = RunResult(RunRequest("test", ExecutionMode.METAL, values), workload(2048),
            BackendInfo(null, "test GPU"), CheckedSummary(emptyList()), 0, 10_000_000, 10_000_000)
        val cpu = gpu.copy(request = gpu.request.copy(mode = ExecutionMode.CPU_PARALLEL), computeNs = 20_000_000)
        fun render(result: RunResult): String {
            output.clear()
            history.record(result)
            renderer.render(result, "Test")
            return output.toString()
        }
        assertFalse(render(gpu).contains("speed advantage"))
        assertFalse(render(cpu.copy(request = cpu.request.copy(visualizationEnabled = true))).contains("speed advantage"))
        assertFalse(render(cpu.copy(descriptor = workload(4096))).contains("speed advantage"))
        assertContains(render(cpu), "GPU speed advantage: 2.0x")
        assertContains(render(gpu.copy(computeNs = 60_000_000)), "CPU speed advantage: 3.0x")
        render(cpu.copy(computeNs = 60_000_000_000L))
        assertContains(render(gpu.copy(computeNs = 60_200_000_000L)), "CPU finished 200 ms sooner.")
    }

    @Test fun rendersResultAndUnavailableValues() {
        val registry = AlgorithmRegistry(emptyList())
        var output = ""
        val io = object : TerminalIo { override fun readLine(): String? = null; override fun write(text: String) { output += text } }
        val values = ParameterValues(emptyMap())
        val descriptor = RunDescriptor(ComparisonKey("test", "1", "x=1", "FP32", "work"), values, emptyMap(), WorkDefinition("work", "samples", 1.0, "samples/s"), null, 3, "batches", listOf(ResultFieldDefinition("sum", "Sum", "units")))
        val result = RunResult(RunRequest("test", ExecutionMode.CPU_SINGLE, values), descriptor, BackendInfo(1, null), CheckedSummary(listOf(ResultFieldValue("sum", "7"))), 2, 0, 4)
        ResultRenderer(io, RunHistory(registry)).render(result, "Test")
        assertTrue(output.contains("Work: N/A (exact work unavailable) samples"))
        for (text in listOf("Test", "x=1", "FP32", "1", "3 batches", "samples", "Preparation", "Calculation", "Total", "N/A", "baseline", "Sum: 7 units")) assertTrue(output.contains(text), text)
    }
    @Test fun rendersExactTimesAndCalculatedRate() {
        val registry = AlgorithmRegistry(emptyList())
        var output = ""
        val io = object : TerminalIo {
            override fun readLine(): String? = null
            override fun write(text: String) { output += text }
        }
        val values = ParameterValues(emptyMap())
        val work = WorkDefinition("work", "samples", 1.0, "samples/s")
        val descriptor = RunDescriptor(ComparisonKey("test", "1", "x=1", "FP32", "work"), values,
            emptyMap(), work, 12, 3, "batches", listOf(ResultFieldDefinition("sum", "Sum", "units")))
        val result = RunResult(RunRequest("test", ExecutionMode.CPU_SINGLE, values), descriptor,
            BackendInfo(1, null), CheckedSummary(listOf(ResultFieldValue("sum", "7"))), 200, 2_000_000_000, 3_000_000_000)
        ResultRenderer(io, RunHistory(registry)).render(result, "Test")
        assertTrue(output.contains("Preparation: 200 ns"))
        assertTrue(output.contains("Calculation: 2 s"))
        assertTrue(output.contains("Total: 3 s"))
        assertTrue(output.contains("Rate: 6.0 samples/s"))
        assertTrue(output.contains("Completed: 3 batches"))
        assertTrue(output.contains("Work: 12 samples"))
    }
}
