package dev.macmetaldemo.monitoring

import kotlin.test.*

class CpuCounterMathTest {
    @Test fun coreDeltasIncludeNiceAndIdle() {
        assertEquals(40.0, coreLoad(CpuTicks(0u, 0u, 0u, 0u), CpuTicks(20u, 10u, 10u, 60u)))
        assertNull(coreLoad(CpuTicks(0u, 0u, 0u, 0u), CpuTicks(0u, 0u, 0u, 0u)))
    }

    @Test fun unsignedCountersWrap() {
        assertEquals(100.0, coreLoad(CpuTicks(UInt.MAX_VALUE - 2u, 0u, 0u, 0u), CpuTicks(2u, 0u, 0u, 0u)))
        assertEquals(50.0, coreLoad(CpuTicks(UInt.MAX_VALUE - 2u, 0u, 0u, 0u), CpuTicks(2u, 0u, 0u, 5u)))
    }

    @Test fun processUsesOneCoreScale() {
        assertEquals(800.0, processLoad(0, 1_600_000_000, 200_000_000))
        assertNull(processLoad(-1, 1, 1))
        assertNull(processLoad(2, 1, 1))
        assertNull(processLoad(0, 1, 0))
        assertNull(processLoad(0, 1, -1))
    }
}
