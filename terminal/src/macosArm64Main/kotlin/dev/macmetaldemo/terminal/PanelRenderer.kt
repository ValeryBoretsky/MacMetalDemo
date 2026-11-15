package dev.macmetaldemo.terminal

import dev.macmetaldemo.domain.*
import kotlin.math.floor

fun renderBar(percent: Double?, ascii: Boolean): String {
    if (percent == null || !percent.isFinite()) return "N/A"
    val count = floor(percent.coerceIn(0.0, 100.0) / 10.0 + 0.5).toInt()
    return (if (ascii) "#" else "■").repeat(count) + (if (ascii) "." else "□").repeat(10 - count)
}

class PanelRenderer {
    private val history = GpuHistory()
    internal fun recordGpu(snapshot: RunSnapshot, resources: ResourceSnapshot) {
        val id = snapshot.backend?.deviceRegistryId
        history.add(if (id == null) null else resources.gpus.firstOrNull { it.registryId == id }?.percent)
    }
    internal fun recordMissingGpu() = history.add(null)
    fun lines(snapshot: RunSnapshot, resources: ResourceSnapshot, nowNs: Long, ascii: Boolean, capabilities: TerminalCapabilities? = null): List<String> {
        val lines = mutableListOf<String>()
        val progress = snapshot.progress
        val elapsed = if (snapshot.phase == RunPhase.RUNNING) {
            snapshot.computeStartedAtNs?.let { (nowNs - it).coerceAtLeast(0L) } ?: snapshot.computeElapsedNs
        } else snapshot.computeElapsedNs
        val eta = if (progress != null && progress.completedUnits > 0 && elapsed != null) {
            (elapsed.toDouble() * (progress.totalUnits - progress.completedUnits) / progress.completedUnits).toLong()
        } else null
        if (progress != null) {
            val percent = 100.0 * progress.completedUnits / progress.totalUnits
            lines.add("Progress: ${percent.toInt()}%  ${progress.completedUnits}/${progress.totalUnits}  Elapsed: ${formatDuration(elapsed)}  ETA: ${formatDuration(eta)}")
            val work = progress.completedWork
            val descriptor = snapshot.descriptor
            if (work != null && elapsed != null && elapsed > 0 && descriptor?.exactWorkCount != null) {
                lines.add("Rate: ${work.toDouble() * 1_000_000_000.0 / elapsed / descriptor.work.rateDivisor} ${descriptor.work.rateLabel}")
            } else lines.add("Rate: N/A")
        } else lines.add(snapshot.phase.toString())
        val gpu = snapshot.gpu
        if (gpu != null) {
            val id = snapshot.backend?.deviceRegistryId
            val sample = if (id == null) null else resources.gpus.firstOrNull { it.registryId == id }
            val fresh = nowNs >= resources.sampledAtNs && nowNs - resources.sampledAtNs < 1_500_000_000L
            val value = sample?.percent?.takeIf { fresh && it.isFinite() && it in 0.0..100.0 }
            val count = sample?.coreCount?.let { "  $it cores" } ?: ""
            lines.add("GPU: ${snapshot.backend?.deviceName ?: "N/A"}  ${gpu.state}$count")
            lines.add("System GPU  ${value?.let { "${it.toInt()}%" } ?: "N/A"}")
            val width = capabilities?.columns?.let { (it - 2).coerceIn(0, 20) } ?: 20
            if (width > 0) lines.addAll(history.rows(ascii, width).filter { it.isNotEmpty() })
            lines.add("GPU history: 500 ms per column, 20% per row")
            lines.add("GPU command duration: ${formatDuration(gpu.lastDurationNs)}")
        }
        val cpuLines = resources.cores.map { core ->
            val value = core.percent
            if (value == null || !value.isFinite()) "System CPU ${core.index}  N/A"
            else "System CPU ${core.index}  [${renderBar(value, ascii)}]  ${value.toInt()}%"
        }
        val compact = capabilities != null && capabilities.tty && capabilities.columns > 0 &&
            (gpu != null || lines.size + cpuLines.size + 2 >= capabilities.rows)
        val cellWidth = (cpuLines.maxOfOrNull { it.length } ?: 0) + 2
        val columns = if (compact && cellWidth > 0) ((capabilities.columns - 1) / cellWidth).coerceAtLeast(1) else 1
        cpuLines.chunked(columns).forEach { row ->
            lines.add(row.mapIndexed { index, value ->
                if (index == row.lastIndex) value else value.padEnd(cellWidth)
            }.joinToString(""))
        }
        val process = resources.processPercent?.let { "${it.toInt()}%" } ?: "N/A"
        lines.add("App CPU  $process  100% = one logical CPU")
        resources.unavailableReason?.let { lines.add(it) }
        return lines
    }
    fun fits(lines: List<String>, capabilities: TerminalCapabilities): Boolean =
        capabilities.supportsScreen() && lines.size < capabilities.rows &&
            lines.all { it.length < capabilities.columns }
}
