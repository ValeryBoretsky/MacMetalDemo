package dev.macmetaldemo.nbody

import dev.macmetaldemo.nbody.domain.*
import dev.macmetaldemo.nbody.cpu.calculateRange
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.*

class NBodyMathTest {
    @Test fun generatorAndLayout() {
        val g = Lcg32()
        assertEquals(1083814273u, g.nextUInt())
        assertEquals(378494188u, g.nextUInt())
        assertEquals(2479403867u, g.nextUInt())
        val state = generateInitialState(NBodyConfig(2, 1))
        assertEquals(8, state.positionMass.size)
        assertEquals(8, state.velocity.size)
        assertEquals(2f * (1083814273u shr 8).toFloat() / 16777216f - 1f, state.positionMass[0])
        assertEquals(2f * (378494188u shr 8).toFloat() / 16777216f - 1f, state.positionMass[1])
        assertEquals(2f * (2479403867u shr 8).toFloat() / 16777216f - 1f, state.positionMass[2])
        assertEquals(16, (state.positionMass.size / 2) * Float.SIZE_BYTES)
        assertEquals(16, (state.velocity.size / 2) * Float.SIZE_BYTES)
        assertEquals(listOf(0.5f, 0.5f), listOf(state.positionMass[3], state.positionMass[7]))
        assertTrue(state.velocity.all { it == 0f })
    }
    @Test fun twoParticlesMatchDoubleReference() {
        val positions = floatArrayOf(0.25f, -0.5f, 0.75f, 0.5f, 1f, 0.25f, -0.25f, 0.5f)
        val velocities = floatArrayOf(0.1f, -0.2f, 0.3f, 0f, -0.1f, 0.2f, -0.3f, 0f)
        val old = ParticleState(positions.copyOf(), velocities.copyOf())
        val next = ParticleState(FloatArray(8), FloatArray(8))
        calculateRange(NBodyConfig(2, 1), old, next, 0, 2)
        // This CPU reference tolerance is separate from the GPU tolerance.
        for (i in 0..1) {
            val offset = i * 4
            val other = (1 - i) * 4
            val delta = DoubleArray(3) { positions[other + it].toDouble() - positions[offset + it].toDouble() }
            val q = delta.sumOf { it * it } + 0.01 * 0.01
            for (axis in 0..2) {
                val acceleration = 0.5 * delta[axis] / (q * sqrt(q))
                val expectedVelocity = velocities[offset + axis].toDouble() + acceleration * 0.001
                val expectedPosition = positions[offset + axis].toDouble() + expectedVelocity * 0.001
                assertTrue(abs(next.velocity[offset + axis] - expectedVelocity) <= 1e-6 + 1e-5 * abs(expectedVelocity))
                assertTrue(abs(next.positionMass[offset + axis] - expectedPosition) <= 1e-6 + 1e-5 * abs(expectedPosition))
            }
            assertEquals(positions[offset + 3], next.positionMass[offset + 3])
            assertEquals(0f, next.velocity[offset + 3])
        }
        assertTrue(next.velocity[0] > velocities[0])
        assertTrue(next.velocity[4] < velocities[4])
        assertContentEquals(positions, old.positionMass)
        assertContentEquals(velocities, old.velocity)
    }
    @Test fun rangeAndCoincidentPositions() {
        val old = ParticleState(floatArrayOf(0f,0f,0f,0.5f, 0f,0f,0f,0.5f), FloatArray(8))
        val next = ParticleState(FloatArray(8) { 7f }, FloatArray(8) { 7f })
        calculateRange(NBodyConfig(2,1), old, next, 1, 2)
        assertContentEquals(FloatArray(4) { 7f }, next.positionMass.copyOfRange(0, 4))
        assertContentEquals(FloatArray(4) { 7f }, next.velocity.copyOfRange(0, 4))
        assertContentEquals(floatArrayOf(0f, 0f, 0f, 0.5f), next.positionMass.copyOfRange(4, 8))
        assertContentEquals(FloatArray(4), next.velocity.copyOfRange(4, 8))
        assertEquals(0f, next.velocity[7])
    }
}
