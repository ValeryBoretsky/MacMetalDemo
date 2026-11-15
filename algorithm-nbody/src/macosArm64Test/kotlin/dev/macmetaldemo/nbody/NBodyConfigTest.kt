package dev.macmetaldemo.nbody

import dev.macmetaldemo.nbody.domain.*
import kotlin.test.*

class NBodyConfigTest {
    @Test fun workAndBounds() {
        assertEquals(268419072000L, NBodyConfig(16384, 1000).workCount())
        NBodyConfig(2, 1)
        NBodyConfig(262144, 1000000)
        assertFails { NBodyConfig(1, 1) }
        assertFails { NBodyConfig(262145, 1) }
        assertFails { NBodyConfig(2, 0) }
        assertFails { NBodyConfig(2, 1000001) }
        assertFails { checkedProduct(Long.MAX_VALUE, 2) }
        assertFails { AllocationBudget.arrayBytes(Long.MAX_VALUE, 4) }
    }
}
