package dev.macmetaldemo.terminal

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.*
import kotlin.test.*

class ResultRendererTest {
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
