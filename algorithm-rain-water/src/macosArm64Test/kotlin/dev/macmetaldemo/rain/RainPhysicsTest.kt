package dev.macmetaldemo.rain

import kotlin.test.*

private class Field {
    val config = RainConfig(32, 24, 4, 300, 42u, 1)
    val walls = FloatArray(config.cellCount)
    var water = FloatArray(config.cellCount)
    private var next = water.copyOf()
    val input = IntArray(32)
    val drain = IntArray(32)
    var tick = 0

    fun index(x: Int, y: Int) = y * 32 + x
    fun wall(x: Int, y: Int) { walls[index(x, y)] = 1f }
    fun wet(x: Int, y: Int) = water[index(x, y)].toInt()
    fun advance(counts: FloatArray = FloatArray(config.width)) {
        for (phase in RainPhase.entries) {
            val before = water.sum() + drain.sum() - input.sum()
            updateRainRange(config, walls, water, next, input, drain, counts, tick, phase, 0, config.cellCount)
            assertEquals(before, next.sum() + drain.sum() - input.sum())
            for (i in next.indices) {
                assertTrue(next[i] in 0f..256f)
                if (walls[i] != 0f) assertEquals(0f, next[i])
            }
            val previous = water
            water = next
            next = previous
        }
        tick++
    }
}

class RainPhysicsTest {
    @Test fun localizedRainFollowsEachPlankBeforeReachingTheDrain() {
        val field = Field()
        generateScene(field.config).walls.copyInto(field.walls)
        val planks = rainPlanks(field.config)
        val first = planks.first()
        val inlet = FloatArray(field.config.width)
        inlet[(first.highX + first.lowX) / 2] = 1f
        val spillTicks = IntArray(planks.size)
        var drainTick = 0
        repeat(field.config.ticks) {
            field.advance(inlet)
            for ((index, plank) in planks.withIndex()) {
                val streamX = plank.lowX + plank.direction
                val streamY = plank.surfaceY(plank.lowX)
                if (spillTicks[index] == 0 && field.wet(streamX, streamY) > 0) spillTicks[index] = field.tick
            }
            if (drainTick == 0 && field.drain.sum() > 0) {
                drainTick = field.tick
                val last = planks.last()
                assertTrue(field.drain[last.lowX + last.direction] > 0)
            }
        }
        assertTrue(spillTicks[0] in 1 until spillTicks[1])
        assertTrue(spillTicks[1] < spillTicks[2])
        assertTrue(spillTicks[2] < drainTick)
        assertEquals(field.config.ticks * field.config.rainMass, field.input.sum())
        assertEquals(field.input.sum(), field.water.sum().toInt() + field.drain.sum())
    }

    @Test fun shelfMatchesHandWorkedTransfers() {
        val shelf = Field()
        for (y in 18..23) {
            shelf.wall(0, y)
            shelf.wall(6, y)
        }
        fun expect(f: Field, vararg wet: Triple<Int, Int, Int>) {
            val expected = FloatArray(f.config.cellCount)
            for ((x, y, mass) in wet) expected[f.index(x, 18 + y)] = mass.toFloat()
            assertContentEquals(expected, f.water)
        }
        for (x in 2..4) shelf.wall(x, 20)
        shelf.water[shelf.index(3, 19)] = 16f
        repeat(3) { shelf.advance() }
        expect(shelf, Triple(2,1,4), Triple(3,1,4), Triple(4,1,2), Triple(5,1,2), Triple(1,2,4))
        repeat(3) { shelf.advance() }
        assertEquals(4, shelf.drain.sum())
        expect(shelf, Triple(1,1,1), Triple(2,1,1), Triple(3,1,3), Triple(4,1,2),
            Triple(5,2,1), Triple(1,3,2), Triple(5,4,2))
    }

    @Test fun everyPairOfCellMassesConservesWater() {
        val f = Field()
        val top = f.index(8, 2)
        val output = FloatArray(f.config.cellCount)
        val counts = FloatArray(f.config.width)
        for (a in 0..256) for (b in 0..256) {
            f.water[top] = a.toFloat()
            f.water[top + 32] = b.toFloat()
            updateRainRange(f.config, f.walls, f.water, output, f.input, f.drain, counts, 0, RainPhase.GRAVITY, top, top + 1)
            assertEquals(a - minOf(a, 256 - b), output[top].toInt())
            f.water[top + 1] = b.toFloat()
            f.wall(8, 3)
            f.wall(9, 3)
            updateRainRange(f.config, f.walls, f.water, output, f.input, f.drain, counts, 0, RainPhase.RUNOFF, top, top + 2)
            assertEquals(a - (a - b) / 2, output[top].toInt())
            assertEquals(a + b, (output[top] + output[top + 1]).toInt())
            f.walls[top + 32] = 0f
        }
    }

    @Test fun seededPlanksConnectEachStepAndLeaveSpaceForTheNextStream() {
        for ((width, height) in listOf(32 to 24, 33 to 25, 160 to 96, 32 to 2048, 2048 to 24)) {
            for (seed in listOf(0u, 42u, UInt.MAX_VALUE)) {
                val config = RainConfig(width, height, 4, 1, seed, 4096)
                val walls = generateScene(config).walls
                fun wall(x: Int, y: Int) = walls[y * width + x]
                val planks = rainPlanks(config)
                assertEquals(listOf(1, -1, 1), planks.map { it.direction })
                for (x in 1 until width - 1) {
                    assertEquals(0f, wall(x, 0))
                    assertEquals(0f, wall(x, height - 1))
                }
                for (y in 0 until height) {
                    assertEquals(1f, wall(0, y))
                    assertEquals(1f, wall(width - 1, y))
                }
                for ((index, plank) in planks.withIndex()) {
                    assertTrue(plank.drop > 0)
                    for (distance in 0 until plank.length) {
                        val x = plank.highX + distance * plank.direction
                        val top = plank.surfaceY(x)
                        val adjacent = plank.surfaceY(x + plank.direction)
                        assertEquals(0f, wall(x, top - 1))
                        val surface = if (plank.direction == 1) 2f else 3f
                        for (y in top..adjacent) assertEquals(surface, wall(x, y))
                        assertEquals(surface, wall(x + plank.direction, adjacent))
                    }
                    val streamX = plank.lowX + plank.direction
                    val startY = plank.surfaceY(plank.lowX)
                    val lower = planks.getOrNull(index + 1)
                    if (lower != null) {
                        val distance = (streamX - lower.highX) * lower.direction
                        assertTrue(distance in 1 until lower.length / 2)
                    }
                    val endY = lower?.surfaceY(streamX) ?: height
                    assertTrue(endY > startY)
                    for (y in startY until endY) assertEquals(0f, wall(streamX, y))
                }
            }
        }
        val config = RainConfig(32, 24, 4, 1, 42u, 4096)
        val walls = generateScene(config).walls
        assertContentEquals(walls, generateScene(config).walls)
        assertFalse(walls.contentEquals(generateScene(config.copy(seed = 0u)).walls))
    }
}
