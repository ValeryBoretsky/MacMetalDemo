package dev.macmetaldemo.monitoring

import dev.macmetaldemo.application.MonotonicClock
import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
class MacosClock : MonotonicClock {
    override fun nowNs(): Long = memScoped {
        val time = alloc<timespec>()
        check(clock_gettime(CLOCK_MONOTONIC.toUInt(), time.ptr) == 0) { "Cannot read the monotonic clock." }
        time.tv_sec * 1_000_000_000L + time.tv_nsec
    }
}
