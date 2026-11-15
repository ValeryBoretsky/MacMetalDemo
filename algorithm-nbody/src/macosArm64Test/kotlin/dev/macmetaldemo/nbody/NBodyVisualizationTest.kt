package dev.macmetaldemo.nbody

import kotlin.test.*

class NBodyVisualizationTest {
    @Test fun sampleIncludesEverySmallParticleAndSpreadsLargeInput() {
        assertContentEquals(intArrayOf(0, 1, 2), sampleIndices(3))
        val indices = sampleIndices(16384)
        assertEquals(512, indices.size)
        assertEquals(0, indices.first())
        assertEquals(32, indices[1])
        assertEquals(16352, indices.last())
    }

    @Test fun projectionCombinesDotsAndDiscardsOutOfBounds() {
        val positions = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 100f, 0f, 0f, 1f)
        val frame = projectSamples(positions, 7)
        assertEquals(7L, frame.completedUnits)
        assertTrue(frame.isSet(40, 30))
        assertEquals(1, (0 until 60).sumOf { y -> (0 until 80).count { x -> frame.isSet(x, y) } })
    }
}
