package dev.macmetaldemo.nbody

import dev.macmetaldemo.nbody.domain.ParticleState
import kotlin.test.*

class NBodySummaryTest {
    @Test fun rejectsNonfiniteMassAndPadding() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            for (mass in listOf(true, false)) {
                val state = ParticleState(floatArrayOf(1f, 2f, 3f, 4f), floatArrayOf(5f, 6f, 7f, 0f))
                if (mass) state.positionMass[3] = value else state.velocity[3] = value
                assertFailsWith<IllegalStateException> { summarize(state) }
            }
        }
    }
    @Test fun sumsExcludeFiniteMassAndPadding() {
        val state = ParticleState(floatArrayOf(1f, 2f, 3f, 100f), floatArrayOf(4f, 5f, 6f, 200f))
        assertEquals(listOf("1.0", "2.0", "3.0", "4.0", "5.0", "6.0"), summarize(state).fields.map { it.value })
    }
}
