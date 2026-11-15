package dev.macmetaldemo.application

import dev.macmetaldemo.domain.*

sealed interface ParameterValidation {
    data class Valid(val values: ParameterValues) : ParameterValidation
    data class Invalid(val messages: Map<String, String>) : ParameterValidation
}

interface DemoAlgorithm {
    val visualizationTitle: String? get() = null
    val info: AlgorithmInfo
    val parameters: List<ParameterDefinition>
    val supportedModes: Set<ExecutionMode>
    fun resolveParameter(definition: ParameterDefinition, accepted: ParameterValues): ParameterDefinition = definition
    fun visualizationTitle(values: ParameterValues): String? = visualizationTitle
    fun availability(mode: ExecutionMode): ModeAvailability
    fun validate(values: ParameterValues): ParameterValidation
    fun describe(values: ParameterValues): RunDescriptor
    fun prepare(request: RunRequest, gpu: GpuStatusSink): PreparedRun
}

interface PreparedRun {
    fun attachVisualization(port: VisualizationPort) = Unit
    val backend: BackendInfo
    fun warmUp()
    fun execute(progress: ProgressSink, gpu: GpuStatusSink)
    fun checkAndSummarize(): CheckedSummary
    fun close()
}
