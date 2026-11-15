package dev.macmetaldemo.cpu

import dev.macmetaldemo.domain.ExecutionMode
import kotlin.test.*

class DirectCpuRuntimeTest {
    @Test fun executesCompleteRangeAndCloses() {
        val runtime = DefaultCpuRuntimeFactory().open(ExecutionMode.CPU_SINGLE, 3)
        val values = mutableListOf<Int>()
        runtime.forEachRange(3) { start, end -> for (i in start until end) values.add(i) }
        assertEquals(listOf(0, 1, 2), values)
        runtime.close()
        runtime.close()
        assertFails { runtime.forEachRange(3) { _, _ -> } }
        assertFails { DefaultCpuRuntimeFactory().open(ExecutionMode.METAL, 3) }
    }
}
