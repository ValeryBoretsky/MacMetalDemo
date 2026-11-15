package dev.macmetaldemo.monitoring

import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
internal fun readProcessCpuNs(): Long? = memScoped {
    val usage = alloc<rusage>()
    if (getrusage(RUSAGE_SELF, usage.ptr) != 0) return@memScoped null
    fun timeNs(seconds: Long, micros: Int): Long? {
        if (seconds < 0 || micros !in 0..999999 || seconds > (Long.MAX_VALUE - micros.toLong() * 1000) / 1_000_000_000) return null
        return seconds * 1_000_000_000 + micros.toLong() * 1000
    }
    val user = timeNs(usage.ru_utime.tv_sec, usage.ru_utime.tv_usec) ?: return@memScoped null
    val system = timeNs(usage.ru_stime.tv_sec, usage.ru_stime.tv_usec) ?: return@memScoped null
    if (user > Long.MAX_VALUE - system) null else user + system
}
