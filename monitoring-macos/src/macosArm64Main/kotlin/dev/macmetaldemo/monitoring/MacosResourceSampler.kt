package dev.macmetaldemo.monitoring

import dev.macmetaldemo.application.MonotonicClock
import dev.macmetaldemo.application.ResourceSampler
import dev.macmetaldemo.domain.CoreLoad
import dev.macmetaldemo.domain.ResourceSnapshot

internal interface CpuReaders {
    fun readCores(): List<CpuTicks>?
    fun readProcessNs(): Long?
    fun close()
}

class MacosResourceSampler internal constructor(private val clock: MonotonicClock, private val readers: CpuReaders, private val gpuReader: GpuReader = GpuReader { emptyList() }) : ResourceSampler, AutoCloseable {
    constructor(clock: MonotonicClock) : this(clock, NativeCpuReaders(), GpuRegistryReader())
    private var previousCores: List<CpuTicks>? = null
    private var previousProcess: Long? = null
    private var previousCoreTime: Long? = null
    private var previousProcessTime: Long? = null
    private var knownCoreCount = 0
    private var closed = false

    override fun sample(): ResourceSnapshot {
        if (closed) return ResourceSnapshot(0, emptyList(), null, "CPU sampling is unavailable.")
        val time = try { clock.nowNs() } catch (_: Exception) { -1L }
        var releaseFailed = false
        val cores = try { readers.readCores() } catch (_: CpuReleaseException) {
            releaseFailed = true
            null
        } catch (_: Exception) { null }
        val process = try { readers.readProcessNs() } catch (_: Exception) { null }
        val validCores = cores?.takeIf { it.isNotEmpty() && time >= 0 }
        val oldCores = previousCores
        val oldCoreTime = previousCoreTime
        val coreInterval = validCores != null && oldCores != null && oldCoreTime != null &&
            time > oldCoreTime && oldCores.size == validCores.size
        if (validCores != null) knownCoreCount = validCores.size
        val loads = if (validCores != null) validCores.mapIndexed { index, ticks ->
            CoreLoad(index, if (coreInterval) coreLoad(oldCores[index], ticks) else null)
        } else List(knownCoreCount) { CoreLoad(it, null) }
        previousCores = validCores?.toList()
        previousCoreTime = if (validCores != null) time else null

        val validProcess = process?.takeIf { it >= 0 && time >= 0 }
        val oldProcess = previousProcess
        val oldProcessTime = previousProcessTime
        val processPercent = if (validProcess != null && oldProcess != null && oldProcessTime != null && time > oldProcessTime) {
            processLoad(oldProcess, validProcess, time - oldProcessTime)
        } else null
        previousProcess = validProcess
        previousProcessTime = if (validProcess != null) time else null
        val reason = if (releaseFailed) "Cannot release the CPU counter buffer."
            else if (validCores == null || validProcess == null) "CPU sampling is unavailable." else null
        val gpus = try { gpuReader.read() } catch (_: Exception) { emptyList() }
        return ResourceSnapshot(time, loads, processPercent, reason, gpus)
    }

    override fun close() {
        if (!closed) { closed = true; readers.close() }
    }
}
