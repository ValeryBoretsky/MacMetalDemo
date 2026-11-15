package dev.macmetaldemo.life

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LifeConfigTest {
    @Test
    fun acceptsEndpointsWithoutAllocatingBoards() {
        assertEquals(9, LifeConfig(3, 3, 0, 1, 0u).cellCount)
        val maximum = LifeConfig(4096, 4096, 16777216, 1000000, UInt.MAX_VALUE)
        assertEquals(16777216, maximum.cellCount)
        assertEquals(16777216000000L, maximum.workCount)
    }

    @Test
    fun rejectsInvalidDimensionsPopulationAndGenerations() {
        val invalid = listOf(
            { LifeConfig(2, 3, 0, 1, 0u) },
            { LifeConfig(3, 2, 0, 1, 0u) },
            { LifeConfig(4097, 3, 0, 1, 0u) },
            { LifeConfig(3, Int.MAX_VALUE, 0, 1, 0u) },
            { LifeConfig(3, 3, -1, 1, 0u) },
            { LifeConfig(3, 3, 10, 1, 0u) },
            { LifeConfig(3, 3, 0, 0, 0u) },
            { LifeConfig(3, 3, 0, 1000001, 0u) },
        )
        for (create in invalid) assertFailsWith<IllegalArgumentException> { create() }
    }

    @Test
    fun multiplicationChecksOverflowBeforeCalculating() {
        assertEquals(Long.MAX_VALUE, checkedLifeProduct(Long.MAX_VALUE, 1))
        assertEquals(0, checkedLifeProduct(Long.MAX_VALUE, 0))
        assertFailsWith<IllegalArgumentException> { checkedLifeProduct(Long.MAX_VALUE, 2) }
        assertFailsWith<IllegalArgumentException> { checkedLifeProduct(-1, 1) }
        assertFailsWith<IllegalArgumentException> { checkedLifeProduct(1, -1) }
    }

    @Test
    fun warmUpUsesItsOwnSmallWorkload() {
        assertEquals(LifeConfig(16, 16, 50, 1, 42u), LifeConfig.WARM_UP)
    }
}
