package dev.macmetaldemo.rain

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RainModelTest {
    private val config = RainConfig(32, 24, 4, 1, 42u, 4096)

    @Test
    fun configChecksLimitsBeforeAllocation() {
        assertEquals(768, config.cellCount)
        assertEquals(768L, config.workCount)
        assertEquals(4194304000000L, RainConfig(2048, 2048, 16, 1000000, UInt.MAX_VALUE, 4096).workCount)
        for (invalid in listOf(
            { RainConfig(31, 24, 4, 1, 0u, 4096) },
            { RainConfig(2049, 24, 4, 1, 0u, 4096) },
            { RainConfig(32, 23, 4, 1, 0u, 4096) },
            { RainConfig(32, 2049, 4, 1, 0u, 4096) },
            { RainConfig(32, 24, 0, 1, 0u, 4096) },
            { RainConfig(32, 24, 17, 1, 0u, 4096) },
            { RainConfig(32, 24, 4, 0, 0u, 4096) },
            { RainConfig(32, 24, 4, 1000001, 0u, 4096) },
            { RainConfig(32, 24, 4, 1, 0u, 0) },
            { RainConfig(32, 24, 4, 1, 0u, 16385) },
        )) assertFailsWith<IllegalArgumentException> { invalid() }
    }

    @Test
    fun thousandsOfDropsUseSeedAndTickAcrossTheOpenInlet() {
        assertEquals(0u, mixRain(0u))
        assertEquals(0x172733c2u, mixRain(42u))
        assertEquals(0x6768824au, mixRain(UInt.MAX_VALUE))
        val counts = FloatArray(config.width) { -1f }
        fillRainCounts(config, 0, counts)
        val expected = intArrayOf(
            0, 155, 140, 134, 119, 139, 142, 140, 157, 141, 131, 141, 144, 129, 159, 161,
            141, 129, 120, 141, 123, 124, 110, 140, 151, 143, 127, 135, 116, 130, 134, 0,
        )
        assertContentEquals(expected, IntArray(counts.size) { counts[it].toInt() })
        val first = counts.copyOf()
        fillRainCounts(config, 1, counts)
        assertTrue(!first.contentEquals(counts))
        fillRainCounts(config.copy(seed = 0u), 0, counts)
        assertTrue(!first.contentEquals(counts))
        fillRainCounts(config, 0, counts)
        assertContentEquals(first, counts)
        for (drops in listOf(1, 4096, 16384)) {
            fillRainCounts(config.copy(dropsPerTick = drops), 999999, counts)
            assertEquals(drops.toFloat(), counts.sum())
            assertEquals(0f, counts.first())
            assertEquals(0f, counts.last())
        }
    }

    @Test
    fun heavyRainFillsEachInletCellUpToItsCapacity() {
        val counts = FloatArray(config.width)
        fillRainCounts(config, 0, counts)
        val old = FloatArray(config.cellCount)
        old[4] = 255f
        val admitted = IntArray(config.width)
        val next = phase(old, generateScene(config).walls, RainPhase.ADMIT_RAIN, counts = counts, admitted = admitted)
        assertEquals((config.width - 2) * 256f, next.sum())
        assertEquals(1, admitted[4])
        assertEquals((next.sum() - old.sum()).toInt(), admitted.sum())
        val rejected = counts.sum() * config.rainMass - admitted.sum()
        assertEquals(8959f, rejected)
    }

    @Test
    fun columnCountersKeepIndividualUnitsBeyondTheFloatPrecisionLimit() {
        val initial = 1 shl 24
        val admitted = IntArray(config.width) { initial }
        val drained = IntArray(config.width) { initial }
        val old = FloatArray(config.cellCount)
        old[4] = 255f
        old[cell(4, 23)] = 1f
        val walls = FloatArray(config.cellCount)
        val counts = FloatArray(config.width).apply { this[4] = 1f }
        val wet = phase(old, walls, RainPhase.ADMIT_RAIN, counts = counts, admitted = admitted)
        phase(wet, walls, RainPhase.DRAIN, drained = drained)
        assertEquals(initial + 1, admitted[4])
        assertEquals(initial + 1, drained[4])
    }

    @Test
    fun slopesMoveWaterDownhillIntoDryAndNearlyFullCells() {
        for (direction in listOf(-1, 1)) {
            val walls = FloatArray(config.cellCount)
            val source = if (direction == 1) cell(10, 5) else cell(11, 5)
            val target = source + direction
            walls[source + config.width] = if (direction == 1) 2f else 3f
            val old = FloatArray(config.cellCount)
            old[source] = 1f
            val drop = phase(old, walls, RainPhase.RUNOFF)
            assertEquals(0f, drop[source])
            assertEquals(1f, drop[target])
            old[source] = 10f
            old[target] = 250f
            val full = phase(old, walls, RainPhase.RUNOFF)
            assertEquals(4f, full[source])
            assertEquals(256f, full[target])
            assertContentEquals(full, phase(full, walls, RainPhase.RUNOFF))
        }
    }

    @Test
    fun gravityMovesAnIsolatedDropDownOneCell() {
        val old = FloatArray(config.cellCount)
        old[cell(10, 5)] = 9f
        val next = phase(old, FloatArray(config.cellCount), RainPhase.GRAVITY)
        assertEquals(0f, next[cell(10, 5)])
        assertEquals(9f, next[cell(10, 6)])
        assertEquals(9f, next.sum())
    }

    @Test
    fun gravityFillsLowerCellWithoutExceedingCapacity() {
        val old = FloatArray(config.cellCount)
        val walls = FloatArray(config.cellCount)
        old[cell(10, 5)] = 9f
        old[cell(10, 6)] = 252f
        walls[cell(10, 7)] = 1f
        val next = phase(old, walls, RainPhase.GRAVITY)
        assertEquals(5f, next[cell(10, 5)])
        assertEquals(256f, next[cell(10, 6)])
        assertEquals(261f, next.sum())
    }

    @Test
    fun solidFaceStopsGravity() {
        val old = FloatArray(config.cellCount)
        val walls = FloatArray(config.cellCount)
        old[cell(10, 5)] = 9f
        walls[cell(10, 6)] = 1f
        val next = phase(old, walls, RainPhase.GRAVITY)
        assertContentEquals(old, next)
    }

    @Test
    fun horizontalFlowRequiresSupportUnderTheDonor() {
        val old = FloatArray(config.cellCount)
        val walls = FloatArray(config.cellCount)
        old[cell(10, 5)] = 9f
        assertContentEquals(old, phase(old, walls, RainPhase.RUNOFF))
        walls[cell(10, 6)] = 1f
        val supported = phase(old, walls, RainPhase.RUNOFF)
        assertEquals(5f, supported[cell(10, 5)])
        assertEquals(4f, supported[cell(11, 5)])
        assertEquals(9f, supported.sum())
    }

    @Test
    fun pairParityChangesWhichNeighborReceivesWater() {
        val old = FloatArray(config.cellCount)
        val walls = FloatArray(config.cellCount)
        old[cell(10, 5)] = 16f
        walls[cell(10, 6)] = 1f
        val even = phase(old, walls, RainPhase.RUNOFF, tick = 0)
        val odd = phase(old, walls, RainPhase.RUNOFF, tick = 1)
        assertEquals(8f, even[cell(11, 5)])
        assertEquals(0f, even[cell(9, 5)])
        assertEquals(8f, odd[cell(9, 5)])
        assertEquals(0f, odd[cell(11, 5)])
    }

    @Test
    fun solidFacesStopHorizontalFlowInBothDirections() {
        for (donorX in listOf(10, 11)) {
            val old = FloatArray(config.cellCount)
            val walls = FloatArray(config.cellCount)
            old[cell(donorX, 5)] = 16f
            walls[cell(donorX, 6)] = 1f
            walls[cell(if (donorX == 10) 11 else 10, 5)] = 1f
            assertContentEquals(old, phase(old, walls, RainPhase.RUNOFF))
        }
    }

    @Test
    fun rainCountsOnlyAdmittedMassWhenPacketsCollide() {
        val old = FloatArray(config.cellCount)
        old[4] = 255f
        val admitted = IntArray(config.width)
        val drained = IntArray(config.width)
        val next = phase(old, FloatArray(config.cellCount), RainPhase.ADMIT_RAIN,
            counts = FloatArray(config.width).also { it[4] = 6f }, admitted = admitted, drained = drained)
        assertEquals(256f, next[4])
        assertEquals(1, admitted.sum())
        assertEquals(0, drained.sum())
    }

    @Test
    fun drainCountsRemovedWaterByColumn() {
        val old = FloatArray(config.cellCount)
        old[cell(4, 23)] = 7f
        old[cell(6, 23)] = 11f
        old[cell(4, 22)] = 3f
        val admitted = IntArray(config.width) { 2 }
        val drained = IntArray(config.width)
        val next = phase(old, FloatArray(config.cellCount), RainPhase.DRAIN, admitted = admitted, drained = drained)
        assertEquals(3f, next.sum())
        assertEquals(7, drained[4])
        assertEquals(11, drained[6])
        assertEquals(64, admitted.sum())
    }

    @Test
    fun eachRangeWritesOnlyItsOwnCells() {
        val old = FloatArray(config.cellCount)
        old[cell(10, 5)] = 9f
        val unchanged = old.copyOf()
        val next = FloatArray(config.cellCount) { -1f }
        updateRainRange(config, FloatArray(config.cellCount), old, next, IntArray(32), IntArray(32),
            FloatArray(config.width), 0, RainPhase.GRAVITY, cell(10, 5), cell(10, 6) + 1)
        assertContentEquals(unchanged, old)
        assertEquals(-1f, next[cell(10, 5) - 1])
        assertEquals(0f, next[cell(10, 5)])
        assertEquals(9f, next[cell(10, 6)])
        assertEquals(-1f, next[cell(10, 6) + 1])
    }

    private fun cell(x: Int, y: Int): Int = y * config.width + x

    private fun phase(
        old: FloatArray,
        walls: FloatArray,
        phase: RainPhase,
        tick: Int = 0,
        counts: FloatArray = FloatArray(config.width),
        admitted: IntArray = IntArray(config.width),
        drained: IntArray = IntArray(config.width),
    ): FloatArray {
        val next = FloatArray(config.cellCount)
        updateRainRange(config, walls, old, next, admitted, drained, counts, tick, phase, 0, config.cellCount)
        assertTrue(next.all { it in 0f..256f })
        return next
    }
}
