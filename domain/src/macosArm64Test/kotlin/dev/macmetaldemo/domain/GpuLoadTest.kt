package dev.macmetaldemo.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class GpuLoadTest {
    @Test fun snapshotCopiesGpuSamples() {
        val samples = mutableListOf(GpuLoad(7uL, 16, 42.0))
        val snapshot = ResourceSnapshot(1, emptyList(), null, null, samples)
        samples.clear()
        assertEquals(1, snapshot.gpus.size)
        (snapshot.gpus as MutableList).clear()
        assertEquals(1, snapshot.gpus.size)
    }
}
