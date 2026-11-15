package dev.macmetaldemo.domain

data class WorkDefinition(val id: String, val unit: String, val rateDivisor: Double, val rateLabel: String)
data class ComparisonKey(val algorithmId: String, val modelVersion: String, val canonicalParameters: String, val numericalSettings: String, val workDefinitionId: String)
data class RunRequest(
    val algorithmId: String,
    val mode: ExecutionMode,
    val parameters: ParameterValues,
    val visualizationEnabled: Boolean = false,
)
data class ResultFieldDefinition(val id: String, val label: String, val unit: String?)
data class ResultFieldValue(val id: String, val value: String)

class RunDescriptor(
    val key: ComparisonKey, val parameters: ParameterValues, numericalSettings: Map<String, String>,
    val work: WorkDefinition, val exactWorkCount: Long?, val progressTotal: Long,
    val progressUnit: String, resultFields: List<ResultFieldDefinition>,
) {
    private val storedSettings = numericalSettings.toMap()
    private val storedFields = resultFields.toList()
    val numericalSettings: Map<String, String> get() = storedSettings.toMutableMap()
    val resultFields: List<ResultFieldDefinition> get() = storedFields.toMutableList()
    init {
        require(progressTotal > 0)
        require(resultFields.map { it.id }.distinct().size == resultFields.size)
    }
}

data class RunProgress(val completedUnits: Long, val totalUnits: Long, val completedWork: Long?) {
    init { require(totalUnits > 0) }
}
data class BackendInfo(val workerCount: Int?, val deviceName: String?, val deviceRegistryId: ULong? = null)
class CheckedSummary(fields: List<ResultFieldValue>) {
    private val storedFields = fields.toList()
    val fields: List<ResultFieldValue> get() = storedFields.toMutableList()
    init { require(fields.map { it.id }.distinct().size == fields.size) }
}
enum class RunPhase { PREPARING, WARMING_UP, RUNNING, CHECKING, SUCCEEDED, FAILED }
enum class GpuCommandState { PREPARING, SUBMITTED, COMPLETED }
data class GpuCommandStatus(val state: GpuCommandState, val lastDurationNs: Long?)
data class RunSnapshot(
    val phase: RunPhase, val descriptor: RunDescriptor?, val backend: BackendInfo?, val progress: RunProgress?,
    val computeStartedAtNs: Long?, val computeElapsedNs: Long?, val etaNs: Long?, val gpu: GpuCommandStatus?,
)
data class RunResult(
    val request: RunRequest, val descriptor: RunDescriptor, val backend: BackendInfo, val summary: CheckedSummary,
    val preparationNs: Long, val computeNs: Long, val totalNs: Long,
    val gpu: GpuCommandStatus? = null,
)
