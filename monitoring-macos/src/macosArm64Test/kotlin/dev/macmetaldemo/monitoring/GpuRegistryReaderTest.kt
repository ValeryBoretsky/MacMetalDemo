@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package dev.macmetaldemo.monitoring

import kotlin.test.*

class GpuRegistryReaderTest {
    @Test fun readsLocalRegistryWithoutElevation() {
        val values = GpuRegistryReader().read()
        val device = platform.Metal.MTLCreateSystemDefaultDevice()
        println("GPU telemetry: $values; Metal registry ID: ${device?.registryID}")
        assertTrue(values.all { it.percent?.let { value -> value in 0.0..100.0 } ?: true })
    }
    @Test fun checksLocalMetalTelemetryWhenRequested() {
        if (platform.posix.getenv("MACMETALDEMO_CHECK_GPU_TELEMETRY") == null) return
        val device = assertNotNull(platform.Metal.MTLCreateSystemDefaultDevice())
        val sample = assertNotNull(GpuRegistryReader().read().firstOrNull { it.registryId == device.registryID })
        val percent = assertNotNull(sample.percent)
        assertTrue(percent in 0.0..100.0)
        println("Matched Metal registry ID: ${device.registryID}, cores: ${sample.coreCount}, utilization: $percent%")
    }
    @Test fun validatesProperties() {
        assertEquals(50.0, validatedGpuLoad(1u, 16, 50.0).percent)
        assertNull(validatedGpuLoad(1u, "16", 101.0).percent)
        assertNull(validatedGpuLoad(1u, "16", 50.0).coreCount)
        assertNull(validatedGpuLoad(1u, 0, Double.NaN).percent)
        assertNull(validatedGpuLoad(1u, null, null).percent)
    }
}
