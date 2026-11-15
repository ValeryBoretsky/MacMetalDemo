package dev.macmetaldemo.cpu

import kotlin.test.*

class RangePartitionTest {
    @Test fun dividesUnevenWorkAndCoversEachIndex() {
        val ranges = partitionRanges(17, 4)
        assertEquals(listOf(IndexRange(0, 5), IndexRange(5, 9), IndexRange(9, 13), IndexRange(13, 17)), ranges)
        val visits = IntArray(17)
        ranges.forEach { range -> (range.start until range.endExclusive).forEach { visits[it]++ } }
        assertContentEquals(IntArray(17) { 1 }, visits)
    }
    @Test fun limitsWorkersToElements() {
        assertEquals(listOf(IndexRange(0, 1), IndexRange(1, 2)), partitionRanges(2, 8))
    }
    @Test fun rejectsNonpositiveCounts() {
        for (value in listOf(0, -1)) {
            assertFailsWith<IllegalArgumentException> { partitionRanges(value, 1) }
            assertFailsWith<IllegalArgumentException> { partitionRanges(1, value) }
        }
    }
}
