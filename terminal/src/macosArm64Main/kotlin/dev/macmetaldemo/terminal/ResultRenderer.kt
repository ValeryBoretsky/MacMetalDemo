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
        for (field in descriptor.resultFields) {
            val value = result.summary.fields.firstOrNull { it.id == field.id }?.value ?: "N/A"
            io.write("${field.label}: $value${field.unit?.let { " $it" } ?: ""}\n")
        }
    }
}
