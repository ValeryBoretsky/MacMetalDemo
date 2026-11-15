package dev.macmetaldemo.monitoring

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSProcessInfo
import platform.posix.usleep
import kotlin.test.*

@OptIn(ExperimentalForeignApi::class)
class MacosHardwareSamplingTest {
    @Test fun readsEachLogicalCpu() {
        val logicalCpus = NSProcessInfo.processInfo.activeProcessorCount.toInt()
        val sampler = MacosResourceSampler(MacosClock())
        try {
            val first = sampler.sample()
            usleep(100_000u)
            val second = sampler.sample()
            println("Hardware logical CPU count: $logicalCpus")
            assertTrue(logicalCpus > 0)
            assertEquals(logicalCpus, first.cores.size)
            assertEquals(logicalCpus, second.cores.size)
            assertTrue(first.cores.all { it.percent == null })
            assertTrue(second.cores.all { it.percent != null && it.percent!! in 0.0..100.0 })
            assertNotNull(second.processPercent)
            assertTrue(second.processPercent!! >= 0.0)
        } finally { sampler.close() }
    }
}
