package dev.macmetaldemo.terminal

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.*

class ResultRenderer(private val io: TerminalIo, private val history: RunHistory) {
    fun render(result: RunResult, algorithmName: String, includeGpu: Boolean = true) {
        val descriptor = result.descriptor
        io.write("Algorithm: $algorithmName\nParameters: ${descriptor.key.canonicalParameters}\nSettings: ${descriptor.key.numericalSettings}\n")
        io.write("Mode: ${result.request.mode}\n")
        if (includeGpu) io.write("Visualization: ${if (result.request.visualizationEnabled) "on" else "off"}\n")
        result.backend.workerCount?.let { io.write("CPU workers: $it\n") }
        result.backend.deviceName?.let { io.write("Device: $it\n") }
        if (includeGpu && result.request.mode == ExecutionMode.METAL) {
            io.write("GPU: ${result.backend.deviceName ?: "N/A"}  ${result.gpu?.state ?: "N/A"}\n")
            io.write("GPU command duration: ${formatDuration(result.gpu?.lastDurationNs)}\n")
        }
        io.write("Completed: ${descriptor.progressTotal} ${descriptor.progressUnit}\n")
        io.write("Work: ${descriptor.exactWorkCount?.toString() ?: "N/A (exact work unavailable)"} ${descriptor.work.unit}\n")
        io.write("Preparation: ${formatDuration(result.preparationNs)}\nCalculation: ${formatDuration(result.computeNs)}\nTotal: ${formatDuration(result.totalNs)}\n")
        io.write("Rate: ${result.workRate()?.toString() ?: "N/A"} ${descriptor.work.rateLabel}\n")
        io.write("Speed increase: ${history.speedIncrease(result)?.toString() ?: "N/A (matching CPU baseline unavailable)"}\n")
        if (result.request.mode != ExecutionMode.CPU_SINGLE) showParallelComparison(result)
        for (field in descriptor.resultFields) {
            val value = result.summary.fields.firstOrNull { it.id == field.id }?.value ?: "N/A"
            io.write("${field.label}: $value${field.unit?.let { " $it" } ?: ""}\n")
        }
    }

    private fun showParallelComparison(result: RunResult) {
        val cpu = history.matchingCalculationNs(result, ExecutionMode.CPU_PARALLEL) ?: return
        val gpu = history.matchingCalculationNs(result, ExecutionMode.METAL) ?: return
        io.write("CPU (all cores): ${formatDuration(cpu)}   GPU (Metal): ${formatDuration(gpu)}\n")
        if (cpu == gpu) {
            io.write("Both modes took the same calculation time.\n")
        } else {
            val faster = if (cpu > gpu) "GPU" else "CPU"
            val ratio = maxOf(cpu, gpu).toDouble() / minOf(cpu, gpu)
            val rounded = kotlin.math.round(ratio * 100) / 100
            if (rounded > 1.0) io.write("$faster speed advantage: ${rounded}x\n")
            else io.write("$faster finished ${formatDuration(maxOf(cpu, gpu) - minOf(cpu, gpu))} sooner.\n")
        }
    }
}
