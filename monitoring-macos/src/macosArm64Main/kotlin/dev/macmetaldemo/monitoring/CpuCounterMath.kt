package dev.macmetaldemo.monitoring

internal data class CpuTicks(val user: UInt, val system: UInt, val nice: UInt, val idle: UInt)

internal fun coreLoad(previous: CpuTicks, current: CpuTicks): Double? {
    val busy = (current.user - previous.user).toLong() + (current.system - previous.system).toLong() +
        (current.nice - previous.nice).toLong()
    val total = busy + (current.idle - previous.idle).toLong()
    return if (total == 0L) null else 100.0 * busy / total
}

internal fun processLoad(previousCpuNs: Long, currentCpuNs: Long, wallDeltaNs: Long): Double? {
    if (previousCpuNs < 0 || currentCpuNs < previousCpuNs || wallDeltaNs <= 0) return null
    return 100.0 * (currentCpuNs - previousCpuNs).toDouble() / wallDeltaNs
}
