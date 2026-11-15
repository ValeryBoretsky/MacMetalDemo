package dev.macmetaldemo.domain

data class CoreLoad(val index: Int, val percent: Double?)
data class GpuLoad(val registryId: ULong, val coreCount: Int?, val percent: Double?, val unavailableReason: String? = null)

class ResourceSnapshot(val sampledAtNs: Long, cores: List<CoreLoad>, val processPercent: Double?, val unavailableReason: String?, gpus: List<GpuLoad> = emptyList()) {
    private val storedGpus = gpus.toList()
    val gpus: List<GpuLoad> get() = storedGpus.toMutableList()
    private val storedCores = cores.toList()
    val cores: List<CoreLoad> get() = storedCores.toMutableList()
}
