package dev.macmetaldemo.monitoring

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.sizeOf

internal class CpuReleaseException(cause: Throwable? = null) : Exception("Cannot release the CPU counter buffer.", cause)

internal class MachBuffer(val coreCount: Int, val infoCount: Long, val value: (Int) -> UInt, val release: (Long) -> Unit)
internal interface MachReader {
    fun acquire(): MachBuffer?
    fun close()
}

@OptIn(ExperimentalForeignApi::class)
internal class MachCpuCounters(private val reader: MachReader) {
    private var closed = false
    fun read(): List<CpuTicks>? {
        val buffer = try { reader.acquire() } catch (error: CpuReleaseException) { throw error } catch (_: Exception) { null } ?: return null
        val elementBytes = sizeOf<IntVar>()
        return try {
            require(buffer.infoCount >= 0 && buffer.infoCount <= Long.MAX_VALUE / elementBytes)
            require(buffer.coreCount > 0 && buffer.coreCount <= Int.MAX_VALUE / 4 && buffer.coreCount.toLong() * 4 <= buffer.infoCount)
            List(buffer.coreCount) { index ->
                val offset = index * 4
                CpuTicks(buffer.value(offset), buffer.value(offset + 1), buffer.value(offset + 3), buffer.value(offset + 2))
            }
        } catch (_: Exception) { null } finally {
            if (buffer.infoCount >= 0 && buffer.infoCount <= Long.MAX_VALUE / elementBytes) {
                try { buffer.release(buffer.infoCount * elementBytes) } catch (error: Exception) {
                    throw CpuReleaseException(error)
                }
            }
        }
    }
    fun close() { if (!closed) { closed = true; reader.close() } }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal class NativeCpuReaders : CpuReaders {
    private val counters = MachCpuCounters(NativeMachReader())
    override fun readCores() = counters.read()
    override fun readProcessNs() = readProcessCpuNs()
    override fun close() = counters.close()
}
