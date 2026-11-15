package dev.macmetaldemo.application

import dev.macmetaldemo.domain.*

class RunHistory(private val registry: AlgorithmRegistry) {
    private val results = mutableMapOf<Triple<ComparisonKey, ExecutionMode, Boolean>, RunResult>()
    fun record(result: RunResult) {
        results[Triple(result.descriptor.key, result.request.mode, result.request.visualizationEnabled)] = result
    }

    fun matchingCalculationNs(result: RunResult, mode: ExecutionMode): Long? {
        return results[Triple(result.descriptor.key, mode, result.request.visualizationEnabled)]?.computeNs?.takeIf { it > 0 }
    }
    fun speedIncrease(result: RunResult): Double? {
        val algorithm = registry.find(result.request.algorithmId) ?: return null
        if (ExecutionMode.CPU_SINGLE !in algorithm.supportedModes) return null
        val baselineNs = matchingCalculationNs(result, ExecutionMode.CPU_SINGLE) ?: return null
        if (result.computeNs <= 0) return null
        return baselineNs.toDouble() / result.computeNs
    }
}

fun RunResult.workRate(): Double? {
    val count = descriptor.exactWorkCount ?: return null
    if (computeNs <= 0 || descriptor.work.rateDivisor <= 0) return null
    return count.toDouble() * 1_000_000_000.0 / computeNs / descriptor.work.rateDivisor
}
